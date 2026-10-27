@echo off
rem tda: one command for the test data agents (Windows). Same dispatch as bin/tda: resolver commands go to TdaCli,
rem everything else to ProfilerCli. TDA_HOME defaults to this repository.
rem The jar is TDA_CLI_JAR, else .tda\tda-cli.jar (the release named by tda.version in pom.xml: mvn -q initialize).
setlocal
set "JAR=%TDA_CLI_JAR%"
if "%JAR%"=="" set "JAR=%~dp0..\.tda\tda-cli.jar"
if "%TDA_HOME%"=="" set "TDA_HOME=%~dp0.."
if not exist "%JAR%" (
  echo tda: %JAR% not found. Fetch it ^(mvn -q initialize^) or set TDA_CLI_JAR. 1>&2
  exit /b 2
)
set "JAVA=java"
if not "%JAVA_HOME%"=="" set "JAVA=%JAVA_HOME%\bin\java"
if "%~1"=="" (
  echo usage: tda ^<command^> [args] 1>&2
  exit /b 2
)
for %%c in (prewarm resolve use release release-test teardown run-id metrics) do (
  if /i "%~1"=="%%c" (
    "%JAVA%" -jar "%JAR%" %*
    exit /b %ERRORLEVEL%
  )
)
"%JAVA%" -cp "%JAR%" com.amla.testdata.profiler.ProfilerCli %*
exit /b %ERRORLEVEL%
