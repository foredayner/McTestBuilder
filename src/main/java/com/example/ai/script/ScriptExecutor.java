package com.example.ai.script;

import com.example.ai.api.MinecraftAI;
import groovy.lang.Binding;
import groovy.lang.Script;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.runtime.MethodClosure;

import java.util.Locale;
import java.util.Optional;

public final class ScriptExecutor {

    public record ExecutionResult(boolean success, String message, boolean securityBlocked) {

        public static ExecutionResult ok() {
            return new ExecutionResult(true, "", false);
        }

        public static ExecutionResult fail(String message) {
            return new ExecutionResult(false, message == null ? "Unknown script error" : message, false);
        }

        public static ExecutionResult blockedBySecurity(String reason) {
            String safeReason = reason == null || reason.isBlank()
                ? "Blocked potentially malicious script"
                : reason;
            return new ExecutionResult(false, safeReason, true);
        }
    }

    private ScriptExecutor() {
    }

    public static ExecutionResult executeScript(String code) {
        Optional<String> preParseFailure = ScriptSecurityValidator.validateSource(code);
        if (preParseFailure.isPresent()) {
            System.out.println("[SECURITY] Pre-parse rejection: " + preParseFailure.get());
            return ExecutionResult.blockedBySecurity(preParseFailure.get());
        }

        try {
            System.out.println("[SCRIPT] Executing AI code...");
            System.out.println("[SCRIPT CODE]\n" + code);

            Binding binding = createBinding();
            Script script = SafeGroovyShellFactory.createShell(binding).parse(code);
            script.run();

            System.out.println("[SCRIPT] Execution complete");
            return ExecutionResult.ok();
        } catch (MultipleCompilationErrorsException compilationException) {
            String reason = extractSecurityReason(compilationException);
            if (reason != null) {
                System.out.println("[SECURITY] Compile-time rejection: " + reason);
                return ExecutionResult.blockedBySecurity(reason);
            }
            System.out.println("[ERROR] Script compilation failed: " + compilationException.getMessage());
            compilationException.printStackTrace();
            return ExecutionResult.fail(compilationException.getMessage());
        } catch (SecurityException securityException) {
            String reason = securityException.getMessage();
            System.out.println("[SECURITY] Sandbox rejection: " + reason);
            return ExecutionResult.blockedBySecurity(reason);
        } catch (Exception exception) {
            System.out.println("[ERROR] Script execution failed: " + exception.getMessage());
            exception.printStackTrace();
            return ExecutionResult.fail(exception.getMessage());
        }
    }

    /** Compile without running (syntax + sandbox rules). Null when the script compiles. */
    public static String compileProblem(String code) {
        Optional<String> preParseFailure = ScriptSecurityValidator.validateSource(code);
        if (preParseFailure.isPresent()) {
            return "not allowed: " + preParseFailure.get();
        }
        try {
            SafeGroovyShellFactory.createShell(createBinding()).parse(code);
            return null;
        } catch (MultipleCompilationErrorsException compilationException) {
            return compilationException.getMessage();
        } catch (Exception exception) {
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private static Binding createBinding() {
        Binding binding = new Binding();
        for (String method : AiScriptApi.METHOD_NAMES) {
            binding.setVariable(method, new MethodClosure(MinecraftAI.class, method));
        }
        return binding;
    }

    private static String extractSecurityReason(MultipleCompilationErrorsException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof SecurityException securityException) {
                return securityException.getMessage();
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("not allowed")) {
                return message;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
