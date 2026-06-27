. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$frontend = Read-FrontendBundle

Require-Contains $mainPy 'def public_error_message(message: object) -> str:' 'Backend must normalize user-facing errors through public_error_message().'
Require-Contains $mainPy '_copimine_fastapi_routing.serialize_response = _copimine_serialize_response_safe' 'FastAPI response serialization must stay wrapped in the safe serializer.'
Require-NotRegex $mainPy 'traceback\.|print_exc\(' 'Backend must not send traceback helpers into the production response path.'
Require-NotRegex $frontend 'Traceback|stack trace|Unhandled rejection' 'Frontend must not render raw stack traces to players.'

Throw-IfErrors 'ValidateCopiMineNoStackTraceToClient'
