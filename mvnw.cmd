@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Begin all REM://
@REM Batch file for Maven Wrapper
@echo off

@REM Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@REM Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto error

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto error

:init
@REM Determine project base dir
set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

@REM Find the wrapper jar or download it
set WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar

if exist "%WRAPPER_JAR%" (
    set LAUNCHER="%WRAPPER_JAR%"
) else (
    @REM If the wrapper jar doesn't exist, download Maven directly
    for /F "usebackq tokens=1,2 delims==" %%A in ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") do (
        if "%%A"=="distributionUrl" set MAVEN_DIST_URL=%%B
    )

    set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6
    if not exist "!MAVEN_HOME!" (
        echo Downloading Maven...
        mkdir "!MAVEN_HOME!" 2>NUL
        powershell -Command "Invoke-WebRequest -Uri '%MAVEN_DIST_URL%' -OutFile '!MAVEN_HOME!\maven.zip'"
        powershell -Command "Expand-Archive -Path '!MAVEN_HOME!\maven.zip' -DestinationPath '!MAVEN_HOME!'"
        del "!MAVEN_HOME!\maven.zip"
    )

    for /d %%i in ("!MAVEN_HOME!\apache-maven-*") do set MAVEN_HOME=%%i
    set MAVEN_CMD="%MAVEN_HOME%\bin\mvn.cmd"
    goto execute
)

@REM Use the wrapper
set MAVEN_CMD="%JAVA_EXE%" %MAVEN_OPTS% -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -cp %LAUNCHER% %WRAPPER_LAUNCHER%
goto execute

:execute
%MAVEN_CMD% %*
if "%ERRORLEVEL%"=="" goto mainEnd
if %ERRORLEVEL% NEQ 0 goto error
goto mainEnd

:error
set ERROR_CODE=1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

exit /b %ERROR_CODE%
