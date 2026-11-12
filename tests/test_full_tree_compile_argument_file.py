"""Run real javac through the gate's argument writer in a Unicode/spaced path."""
from pathlib import Path
import shutil
import subprocess
import pytest
ROOT=Path(__file__).resolve().parents[1]
def test_full_tree_argument_file_preserves_paths_and_utf8(tmp_path):
 shell=shutil.which("powershell.exe") or shutil.which("pwsh")
 compiler=shutil.which("javac")
 if not shell or not compiler:pytest.skip("PowerShell and javac required")
 source=ROOT/"scripts/minecraft/RunFullTreeCompile.ps1"
 content=source.read_text("utf-8")
 folder=tmp_path/"compile space юникод";folder.mkdir()
 java=folder/"Probe.java";java.write_text('public class Probe { public static void main(String[] a){System.out.print("ok");} }',encoding="utf-8")
 out=folder/"output classes";out.mkdir()
 argfile=folder/"javac arguments.txt"
 # Before repair, execute the gate's real writer block, not a handwritten surrogate.
 if "function Write-FullTreeJavaArgumentFile" in content:
  build=r'''
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($source,[ref]$tokens,[ref]$errors)
$definition=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Write-FullTreeJavaArgumentFile'},$true)
. ([scriptblock]::Create($definition.Extent.Text))
Write-FullTreeJavaArgumentFile -Path $argfile -ClassPath $classpath -Sources $sources
'''
 else:
  start=content.index("$lines = @(");end=content.index("\n\ntry {",start)
  build=content[start:end]
 ps=folder/"write.ps1"
 def quote(p):return "'"+str(p).replace("'","''")+"'"
 ps.write_text("$ErrorActionPreference='Stop'\n$source="+quote(source)+"\n$argfile="+quote(argfile)+"\n$classpath="+quote(folder)+"\n$sources=@("+quote(java)+")\n"+build,encoding="utf-8-sig")
 wrote=subprocess.run([shell,"-NoProfile","-ExecutionPolicy","Bypass","-File",str(ps)],capture_output=True,text=True)
 assert wrote.returncode==0,wrote.stdout+wrote.stderr
 compiled=subprocess.run([compiler,"@"+str(argfile),"-d",str(out)],capture_output=True,text=True)
 assert compiled.returncode==0,compiled.stdout+compiled.stderr
 assert (out/"Probe.class").is_file()
