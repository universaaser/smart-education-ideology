$ErrorActionPreference = "Stop"

$verificationScript = Join-Path $PSScriptRoot "verify_project.ps1"
$powerShellCommand = Join-Path $PSHOME "powershell.exe"
if (-not (Test-Path -LiteralPath $powerShellCommand)) {
    $powerShellCommand = Join-Path $PSHOME "pwsh.exe"
}
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ("smartedu-verify-" + [guid]::NewGuid().ToString("N"))
$cases = @(
    @{ Name = "missing-maven"; Missing = "mvn"; Failure = ""; Stages = ""; Exit = 1; Message = "Backend tests unavailable" },
    @{ Name = "missing-typescript"; Missing = "tsc"; Failure = ""; Stages = ""; Exit = 1; Message = "Frontend type check unavailable" },
    @{ Name = "missing-npm"; Missing = "npm"; Failure = ""; Stages = ""; Exit = 1; Message = "Frontend build unavailable" },
    @{ Name = "backend-failure"; Missing = ""; Failure = "mvn"; Stages = "BACKEND"; Exit = 1; Message = "backend tests failed" },
    @{ Name = "typescript-failure"; Missing = ""; Failure = "tsc"; Stages = "BACKEND,TYPES"; Exit = 1; Message = "frontend type check failed" },
    @{ Name = "build-failure"; Missing = ""; Failure = "npm"; Stages = "BACKEND,TYPES,BUILD"; Exit = 1; Message = "frontend build failed" },
    @{ Name = "success-order"; Missing = ""; Failure = ""; Stages = "BACKEND,TYPES,BUILD"; Exit = 0; Message = "Project verification passed" }
)

try {
    foreach ($case in $cases) {
        $caseRoot = Join-Path $fixtureRoot $case.Name
        $toolsDirectory = Join-Path $caseRoot "tools"
        $scriptsDirectory = Join-Path $caseRoot "scripts"
        $typeScriptDirectory = Join-Path $caseRoot "node_modules\.bin"
        New-Item -ItemType Directory -Path $toolsDirectory, $scriptsDirectory, $typeScriptDirectory | Out-Null
        $caseScript = Join-Path $scriptsDirectory "verify_project.ps1"
        Copy-Item -LiteralPath $verificationScript -Destination $caseScript
        foreach ($stage in @(@{ Command = "mvn"; Marker = "BACKEND" }, @{ Command = "tsc"; Marker = "TYPES" }, @{ Command = "npm"; Marker = "BUILD" })) {
            if ($case.Missing -eq $stage.Command) {
                continue
            }
            $commandDirectory = if ($stage.Command -eq "tsc") { $typeScriptDirectory } else { $toolsDirectory }
            $stubExit = if ($case.Failure -eq $stage.Command) { 9 } else { 0 }
            $stubText = @("@echo off", "echo FIXTURE_$($stage.Marker)", "exit /b $stubExit", "") -join [Environment]::NewLine
            [IO.File]::WriteAllText((Join-Path $commandDirectory ($stage.Command + ".cmd")), $stubText, [Text.Encoding]::ASCII)
        }

        # Run each case in a child process so exit and PATH changes stay isolated.
        $startInfo = New-Object Diagnostics.ProcessStartInfo
        $startInfo.FileName = $powerShellCommand
        $startInfo.Arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $caseScript + '"'
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.EnvironmentVariables["PATH"] = $toolsDirectory
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $startInfo
        try {
            [void]$process.Start()
            $stdoutTask = $process.StandardOutput.ReadToEndAsync()
            $stderrTask = $process.StandardError.ReadToEndAsync()
            $process.WaitForExit()
            $caseText = $stdoutTask.Result + $stderrTask.Result
            $caseExit = $process.ExitCode
        } finally {
            $process.Dispose()
        }

        $actualStages = ([regex]::Matches($caseText, "FIXTURE_(BACKEND|TYPES|BUILD)") | ForEach-Object { $_.Groups[1].Value }) -join ","
        if ($caseExit -ne $case.Exit -or $actualStages -cne $case.Stages -or $caseText -notmatch [regex]::Escape($case.Message)) {
            throw "Case $($case.Name) failed: exit=$caseExit stages=$actualStages output=$caseText"
        }
        Write-Host "PASS $($case.Name)"
    }
    Write-Host "Verification flow tests passed (7 cases)."
} finally {
    # Remove only the unique fixture directory created by this test run.
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
        $resolvedTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolvedFixture.StartsWith($resolvedTemp, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Fixture cleanup path is outside the temporary directory."
        }
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
