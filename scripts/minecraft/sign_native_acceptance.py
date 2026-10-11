"""Create and use an external Ed25519 key for native Minecraft acceptance evidence."""

from __future__ import annotations

import argparse
import base64
import ctypes
import errno
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
import time
from contextlib import contextmanager
from pathlib import Path

from cryptography.exceptions import UnsupportedAlgorithm
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from scripts.minecraft.validate_native_acceptance import (
    ACCEPTANCE_PUBLIC_KEY_ENV,
    ACCEPTANCE_SIGNATURE_CONTEXT,
    ACCEPTANCE_SIGNATURE_PATH,
    EVIDENCE_DIRECTORY,
    AcceptanceError,
    MAX_JSON_BYTES,
    MAX_SIGNATURE_BYTES,
    _relative_file,
    _unique_object,
    validate,
)
import rfc8785


class SigningError(ValueError):
    """Raised when acceptance evidence cannot be safely signed."""


MAX_PRIVATE_KEY_BYTES = 16 * 1024


def _read_bounded_bytes(path: Path, maximum: int, description: str, limit_label: str) -> bytes:
    try:
        with path.open("rb") as source:
            raw = source.read(maximum + 1)
    except OSError as exc:
        raise SigningError(f"cannot read {description}: {exc}") from exc
    if len(raw) > maximum:
        raise SigningError(f"{description} exceeds the {limit_label} safety limit")
    return raw


@contextmanager
def _signing_transaction_lock(root: Path):
    normalized_root = os.path.normcase(str(root.resolve()))
    lock_name = hashlib.sha256(normalized_root.encode("utf-8")).hexdigest() + ".lock"
    if os.name == "nt":
        lock_directory = Path(tempfile.gettempdir()) / "CopiMine-Native-Acceptance-Locks"
    else:
        lock_directory = Path(tempfile.gettempdir()) / f"copimine-native-acceptance-locks-{os.getuid()}"
    lock_file = None
    try:
        lock_directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        directory_info = lock_directory.lstat()
        reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
        if (
            not stat.S_ISDIR(directory_info.st_mode)
            or stat.S_ISLNK(directory_info.st_mode)
            or getattr(directory_info, "st_file_attributes", 0) & reparse_flag
        ):
            raise SigningError("native acceptance lock directory must be a regular directory")
        if os.name != "nt":
            if directory_info.st_uid != os.getuid():
                raise SigningError("native acceptance lock directory must belong to the current user")
            lock_directory.chmod(0o700)
            if stat.S_IMODE(lock_directory.stat().st_mode) != 0o700:
                raise SigningError("native acceptance lock directory must have private permissions")
        lock_path = lock_directory / lock_name
        lock_file = lock_path.open("a+b")
        lock_info = lock_path.lstat()
        opened_info = os.fstat(lock_file.fileno())
        if (
            not stat.S_ISREG(lock_info.st_mode)
            or stat.S_ISLNK(lock_info.st_mode)
            or getattr(lock_info, "st_file_attributes", 0) & reparse_flag
            or lock_info.st_nlink != 1
            or (lock_info.st_dev, lock_info.st_ino) != (opened_info.st_dev, opened_info.st_ino)
        ):
            raise SigningError("native acceptance transaction lock must be a regular private file")
        if opened_info.st_size == 0:
            lock_file.write(b"\0")
            lock_file.flush()
        if os.name == "nt":
            import msvcrt

            while True:
                lock_file.seek(0)
                try:
                    msvcrt.locking(lock_file.fileno(), msvcrt.LK_NBLCK, 1)
                    break
                except OSError as exc:
                    if exc.errno not in (errno.EACCES, errno.EAGAIN, errno.EDEADLK):
                        raise SigningError(f"cannot acquire native acceptance transaction lock: {exc}") from exc
                    time.sleep(0.05)
        else:
            import fcntl

            try:
                fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
            except OSError as exc:
                raise SigningError(f"cannot acquire native acceptance transaction lock: {exc}") from exc
    except SigningError:
        if lock_file is not None:
            lock_file.close()
        raise
    except OSError as exc:
        if lock_file is not None:
            lock_file.close()
        raise SigningError(f"cannot establish native acceptance transaction lock: {exc}") from exc

    try:
        yield
    finally:
        try:
            if os.name == "nt":
                lock_file.seek(0)
                msvcrt.locking(lock_file.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)
        except OSError as exc:
            raise SigningError(f"cannot release native acceptance transaction lock: {exc}") from exc
        finally:
            lock_file.close()


class _WindowsSecurityAttributes(ctypes.Structure):
    _fields_ = [
        ("nLength", ctypes.c_uint32),
        ("lpSecurityDescriptor", ctypes.c_void_p),
        ("bInheritHandle", ctypes.c_int),
    ]


def _default_key_path() -> Path:
    if sys.platform == "win32":
        local_app_data = os.environ.get("LOCALAPPDATA")
        if not local_app_data:
            raise SigningError("LOCALAPPDATA is required to choose an external Windows key location")
        config_root = Path(local_app_data)
    else:
        xdg_config_home = os.environ.get("XDG_CONFIG_HOME")
        if xdg_config_home:
            config_root = Path(xdg_config_home)
            if not config_root.is_absolute():
                raise SigningError("XDG_CONFIG_HOME must be an absolute path")
        else:
            config_root = Path.home() / ".config"
    return config_root / "CopiMine" / "minecraft-26.3" / "native-acceptance-ed25519-private.pem"


def _path_is_within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def _checked_external_key_path(path: Path, root: Path, *, must_exist: bool) -> Path:
    root = root.resolve()
    lexical_path = Path(os.path.abspath(path.expanduser()))
    if _path_is_within(lexical_path, root):
        raise SigningError("private key must be stored outside the repository checkout")
    if must_exist:
        try:
            info = lexical_path.lstat()
        except OSError as exc:
            raise SigningError(f"private key cannot be inspected: {exc}") from exc
        reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & reparse_flag:
            raise SigningError("private key must not be a symlink or reparse point")
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
            raise SigningError("private key must be a regular file with exactly one hard link")
        if info.st_size > 16 * 1024:
            raise SigningError("private key file exceeds the 16 KiB safety limit")
        resolved_path = lexical_path.resolve(strict=True)
        if _path_is_within(resolved_path, root):
            raise SigningError("resolved private key path must be outside the repository checkout")
        return resolved_path

    resolved_parent = lexical_path.parent.resolve()
    if _path_is_within(resolved_parent, root):
        raise SigningError("private key parent directory must be outside the repository checkout")
    return resolved_parent / lexical_path.name


def _windows_system_directory() -> Path:
    try:
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        get_system_directory = kernel32.GetSystemDirectoryW
        get_system_directory.argtypes = (ctypes.POINTER(ctypes.c_wchar), ctypes.c_uint)
        get_system_directory.restype = ctypes.c_uint
        buffer = ctypes.create_unicode_buffer(32768)
        length = get_system_directory(buffer, len(buffer))
    except (AttributeError, OSError) as exc:
        raise SigningError(f"cannot determine Windows system directory: {exc}") from exc
    if length == 0 or length >= len(buffer):
        error = ctypes.get_last_error()
        raise SigningError(f"cannot determine Windows system directory (GetSystemDirectoryW error {error})")
    return Path(buffer.value)


def _windows_system_executable(name: str) -> Path:
    if name not in {"whoami.exe", "icacls.exe"}:
        raise SigningError(f"unsupported Windows system executable: {name}")
    executable = _windows_system_directory() / name
    try:
        if not executable.is_file():
            raise SigningError(f"required Windows system executable is unavailable: {executable}")
        return executable.resolve(strict=True)
    except OSError as exc:
        raise SigningError(f"cannot resolve Windows system executable {name}: {exc}") from exc


def _current_user_sid() -> str:
    try:
        whoami = _windows_system_executable("whoami.exe")
        result = subprocess.run(
            [str(whoami), "/user", "/fo", "csv", "/nh"],
            check=True,
            capture_output=True,
            cwd=str(whoami.parent),
        )
        matches = re.findall(rb"(?<![A-Za-z0-9-])S-\d-\d+(?:-\d+)+(?![A-Za-z0-9-])", result.stdout)
        if len(matches) != 1:
            raise SigningError("whoami output does not contain exactly one Windows user SID")
        sid = matches[0].decode("ascii")
    except (OSError, subprocess.CalledProcessError) as exc:
        raise SigningError(f"cannot determine the current Windows user SID: {exc}") from exc
    if not re.fullmatch(r"S-\d-\d+(?:-\d+)+", sid):
        raise SigningError("whoami returned an invalid Windows user SID")
    return sid


def _restrict_private_key_permissions(path: Path) -> None:
    if os.name == "nt":
        sid = _current_user_sid()
        security_descriptor = ctypes.c_void_p()
        security_descriptor_size = ctypes.c_uint32()
        kernel32 = None
        try:
            advapi32 = ctypes.WinDLL("advapi32", use_last_error=True)
            kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
            convert_sddl = advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW
            convert_sddl.argtypes = (
                ctypes.c_wchar_p,
                ctypes.c_uint32,
                ctypes.POINTER(ctypes.c_void_p),
                ctypes.POINTER(ctypes.c_uint32),
            )
            convert_sddl.restype = ctypes.c_int
            sddl = f"D:P(A;;FA;;;{sid})"
            if not convert_sddl(
                sddl,
                1,
                ctypes.byref(security_descriptor),
                ctypes.byref(security_descriptor_size),
            ):
                raise OSError(ctypes.get_last_error(), "cannot construct a protected private-key DACL")

            dacl_present = ctypes.c_int()
            dacl = ctypes.c_void_p()
            dacl_defaulted = ctypes.c_int()
            get_dacl = advapi32.GetSecurityDescriptorDacl
            get_dacl.argtypes = (
                ctypes.c_void_p,
                ctypes.POINTER(ctypes.c_int),
                ctypes.POINTER(ctypes.c_void_p),
                ctypes.POINTER(ctypes.c_int),
            )
            get_dacl.restype = ctypes.c_int
            if not get_dacl(
                security_descriptor,
                ctypes.byref(dacl_present),
                ctypes.byref(dacl),
                ctypes.byref(dacl_defaulted),
            ):
                raise OSError(ctypes.get_last_error(), "cannot read the protected private-key DACL")
            if not dacl_present.value or not dacl.value:
                raise SigningError("protected private-key DACL must explicitly grant only the current user")

            set_security = advapi32.SetNamedSecurityInfoW
            set_security.argtypes = (
                ctypes.c_wchar_p,
                ctypes.c_uint32,
                ctypes.c_uint32,
                ctypes.c_void_p,
                ctypes.c_void_p,
                ctypes.c_void_p,
                ctypes.c_void_p,
            )
            set_security.restype = ctypes.c_uint32
            result = set_security(
                str(path),
                1,  # SE_FILE_OBJECT
                0x00000004 | 0x80000000,  # DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION
                None,
                None,
                dacl,
                None,
            )
            if result:
                raise OSError(result, "SetNamedSecurityInfoW could not replace the private-key DACL")
        except SigningError:
            raise
        except (AttributeError, OSError) as exc:
            raise SigningError(f"cannot restrict private key file permissions: {exc}") from exc
        finally:
            if security_descriptor.value and kernel32 is not None:
                local_free = kernel32.LocalFree
                local_free.argtypes = (ctypes.c_void_p,)
                local_free.restype = ctypes.c_void_p
                local_free(security_descriptor)
    else:
        try:
            path.chmod(0o600)
        except OSError as exc:
            raise SigningError(f"cannot restrict private key file permissions: {exc}") from exc


def _public_key_hex(private_key: Ed25519PrivateKey) -> str:
    return private_key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    ).hex()


def _load_private_key(path: Path) -> Ed25519PrivateKey:
    try:
        key_bytes = _read_bounded_bytes(path, MAX_PRIVATE_KEY_BYTES, "private key", "16 KiB")
        key = serialization.load_pem_private_key(key_bytes, password=None)
    except (OSError, ValueError, TypeError, UnsupportedAlgorithm) as exc:
        raise SigningError(f"cannot load an unencrypted Ed25519 private key: {exc}") from exc
    if not isinstance(key, Ed25519PrivateKey):
        raise SigningError("private key must use Ed25519")
    return key


def _create_windows_private_key_file_descriptor(path: Path) -> int:
    sid = _current_user_sid()
    security_descriptor = ctypes.c_void_p()
    security_descriptor_size = ctypes.c_uint32()
    kernel32 = None
    try:
        advapi32 = ctypes.WinDLL("advapi32", use_last_error=True)
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        convert_sddl = advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW
        convert_sddl.argtypes = (
            ctypes.c_wchar_p,
            ctypes.c_uint32,
            ctypes.POINTER(ctypes.c_void_p),
            ctypes.POINTER(ctypes.c_uint32),
        )
        convert_sddl.restype = ctypes.c_int
        sddl = f"D:P(A;;FA;;;{sid})"
        if not convert_sddl(
            sddl,
            1,
            ctypes.byref(security_descriptor),
            ctypes.byref(security_descriptor_size),
        ):
            raise OSError(ctypes.get_last_error(), "Cannot construct protected private-key DACL")

        attributes = _WindowsSecurityAttributes(
            ctypes.sizeof(_WindowsSecurityAttributes), security_descriptor, False
        )
        create_file = kernel32.CreateFileW
        create_file.argtypes = (
            ctypes.c_wchar_p,
            ctypes.c_uint32,
            ctypes.c_uint32,
            ctypes.POINTER(_WindowsSecurityAttributes),
            ctypes.c_uint32,
            ctypes.c_uint32,
            ctypes.c_void_p,
        )
        create_file.restype = ctypes.c_void_p
        handle = create_file(
            str(path),
            0x40000000,  # GENERIC_WRITE
            0x00000003,  # FILE_SHARE_READ | FILE_SHARE_WRITE
            ctypes.byref(attributes),
            1,  # CREATE_NEW
            0x00000080,  # FILE_ATTRIBUTE_NORMAL
            None,
        )
        handle_value = handle.value if isinstance(handle, ctypes.c_void_p) else handle
        if handle_value in (None, 0, ctypes.c_void_p(-1).value):
            raise OSError(ctypes.get_last_error(), f"CreateFileW could not securely create {path}")
        try:
            import msvcrt

            return msvcrt.open_osfhandle(handle_value, os.O_WRONLY | getattr(os, "O_BINARY", 0))
        except BaseException as conversion_error:
            close_handle = kernel32.CloseHandle
            close_handle.argtypes = (ctypes.c_void_p,)
            close_handle.restype = ctypes.c_int
            close_error = None
            try:
                if not close_handle(handle_value):
                    close_error = OSError(
                        ctypes.get_last_error(),
                        f"CloseHandle could not close securely created private-key file {path}",
                    )
            except BaseException as exc:
                close_error = exc

            removal_error = None
            try:
                path.unlink()
            except FileNotFoundError:
                pass
            except OSError as exc:
                removal_error = exc

            if removal_error is not None:
                details = f"cannot remove partially created private-key file: {removal_error}"
                if close_error is not None:
                    details += f"; cannot close securely created private-key file handle: {close_error}"
                raise SigningError(details) from removal_error
            if close_error is not None:
                raise SigningError(
                    f"cannot close securely created private-key file handle: {close_error}"
                ) from close_error
            raise
    finally:
        if security_descriptor.value and kernel32 is not None:
            local_free = kernel32.LocalFree
            local_free.argtypes = (ctypes.c_void_p,)
            local_free.restype = ctypes.c_void_p
            local_free(security_descriptor)


def _create_private_key_file_descriptor(path: Path) -> int:
    if os.name == "nt":
        return _create_windows_private_key_file_descriptor(path)
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0)
    return os.open(path, flags, 0o600)


def generate_key(path: Path, root: Path) -> str:
    key_path = _checked_external_key_path(path, root, must_exist=False)
    key_path.parent.mkdir(parents=True, exist_ok=True)
    private_key = Ed25519PrivateKey.generate()
    pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    created = False
    descriptor: int | None = None
    try:
        descriptor = _create_private_key_file_descriptor(key_path)
        created = True
        destination = os.fdopen(descriptor, "wb")
        descriptor = None
        with destination:
            _restrict_private_key_permissions(key_path)
            destination.write(pem)
            destination.flush()
            os.fsync(destination.fileno())
    except OSError as exc:
        cleanup_error = _discard_keygen_failure(key_path, descriptor, created)
        descriptor = None
        if created:
            message = f"cannot create external private key: {exc}"
            if cleanup_error is not None:
                message += f"; cleanup failed: {cleanup_error}"
            raise SigningError(message) from exc
        raise SigningError(f"cannot create external private key: {exc}") from exc
    except BaseException as exc:
        cleanup_error = _discard_keygen_failure(key_path, descriptor, created)
        descriptor = None
        if cleanup_error is not None:
            raise SigningError(
                f"cannot clean up external private key after creation failure: {cleanup_error}"
            ) from exc
        raise
    return _public_key_hex(private_key)


def _remove_partial_key(key_path: Path) -> None:
    try:
        key_path.unlink(missing_ok=True)
    except OSError as exc:
        raise SigningError(f"cannot remove incomplete external private key: {exc}") from exc


def _discard_keygen_failure(key_path: Path, descriptor: int | None, created: bool) -> str | None:
    errors = []
    if descriptor is not None:
        try:
            os.close(descriptor)
        except OSError as exc:
            errors.append(f"cannot close incomplete private-key descriptor: {exc}")
    if created:
        try:
            _remove_partial_key(key_path)
        except SigningError as exc:
            errors.append(str(exc))
    return "; ".join(errors) if errors else None


def _canonical_record(record_path: Path) -> bytes:
    raw = _read_bounded_bytes(record_path, MAX_JSON_BYTES, "native acceptance record", "10 MiB")
    try:
        record = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique_object)
        if not isinstance(record, dict):
            raise SigningError("native acceptance record must be a JSON object")
        canonical = rfc8785.dumps(record)
    except (UnicodeError, ValueError, TypeError, rfc8785.CanonicalizationError, AcceptanceError) as exc:
        raise SigningError(f"native acceptance record is invalid: {exc}") from exc
    return canonical


def _replace_bytes_atomically(path: Path, content: bytes, action: str) -> None:
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile("wb", dir=path.parent, prefix=".acceptance-", suffix=".tmp", delete=False) as temporary:
            temporary_path = Path(temporary.name)
            temporary.write(content)
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_path, path)
    except OSError as exc:
        cleanup_error = None
        if temporary_path is not None:
            try:
                temporary_path.unlink(missing_ok=True)
            except OSError as cleanup_exc:
                cleanup_error = cleanup_exc
        detail = f"cannot {action} atomically: {exc}"
        if cleanup_error is not None:
            detail += f"; cannot remove temporary file: {cleanup_error}"
        raise SigningError(detail) from exc


def _write_signature_atomically(signature_path: Path, signature: bytes) -> None:
    if signature_path.exists():
        _read_bounded_bytes(signature_path, MAX_SIGNATURE_BYTES, "native acceptance signature", "1 KiB")
    encoded = base64.b64encode(signature) + b"\n"
    _replace_bytes_atomically(signature_path, encoded, "write detached signature")


def sign_record(root: Path, private_key_path: Path) -> str:
    root = root.resolve()
    with _signing_transaction_lock(root):
        key_path = _checked_external_key_path(private_key_path, root, must_exist=True)
        _restrict_private_key_permissions(key_path)
        record_path = _relative_file(root, EVIDENCE_DIRECTORY + "/acceptance.json", "native acceptance record")
        signature_path = _relative_file(root, ACCEPTANCE_SIGNATURE_PATH, "native acceptance signature", prefix=EVIDENCE_DIRECTORY + "/") if (root / ACCEPTANCE_SIGNATURE_PATH).exists() else root / ACCEPTANCE_SIGNATURE_PATH
        private_key = _load_private_key(key_path)
        canonical_record = _canonical_record(record_path)
        if _read_bounded_bytes(record_path, MAX_JSON_BYTES, "native acceptance record", "10 MiB") != canonical_record:
            raise SigningError("native acceptance record must already use RFC 8785 canonical JSON encoding")
        public_key_hex = _public_key_hex(private_key)
        signature = private_key.sign(ACCEPTANCE_SIGNATURE_CONTEXT + canonical_record)
        encoded_signature = base64.b64encode(signature) + b"\n"
        validate(
            root,
            record_path,
            trusted_public_key_hex=public_key_hex,
            detached_signature=encoded_signature,
        )
        if _read_bounded_bytes(record_path, MAX_JSON_BYTES, "native acceptance record", "10 MiB") != canonical_record:
            raise SigningError("native acceptance record changed during signing validation")
        _write_signature_atomically(signature_path, signature)
        return public_key_hex


def main(argv: list[str] | None = None) -> int:
    default_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=default_root)
    commands = parser.add_subparsers(dest="command", required=True)
    keygen = commands.add_parser("keygen", help="create a protected private key outside the checkout")
    keygen.add_argument("--private-key", type=Path, default=None)
    sign = commands.add_parser("sign", help="validate and sign native acceptance evidence")
    sign.add_argument("--private-key", type=Path, default=None)
    args = parser.parse_args(argv)
    try:
        private_key_path = args.private_key or _default_key_path()
        if args.command == "keygen":
            public_key_hex = generate_key(private_key_path, args.root)
            print(f"{ACCEPTANCE_PUBLIC_KEY_ENV}={public_key_hex}")
            print("Store this public value in protected repository CI settings; keep the private key outside the checkout.")
            return 0
        public_key_hex = sign_record(args.root, private_key_path)
        print("Native acceptance evidence is signed and passes the local validator.")
        print(f"{ACCEPTANCE_PUBLIC_KEY_ENV}={public_key_hex}")
        return 0
    except (SigningError, AcceptanceError) as exc:
        print(f"Native acceptance signing failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
