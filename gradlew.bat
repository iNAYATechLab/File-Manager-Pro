@rem Minimal Gradle wrapper launcher for File-Manager-Pro (Windows).
@echo off
setlocal
set APP_HOME=%~dp0

if defined JAVA_HOME (
    set JAVA=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA=java
)

"%JAVA%" -Xmx64m -Xms64m -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
