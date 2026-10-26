package com.example.ai.script;

import com.example.ai.api.MinecraftAI;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.File;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds a GroovyShell locked down with {@link SecureASTCustomizer}.
 *
 * <p><b>Limitations of SecureASTCustomizer</b> (documented by the Groovy project):
 * <ul>
 *   <li>Groovy is dynamic; AST checks cannot catch every runtime escape (e.g. crafted metaClass usage).</li>
 *   <li>Disallowed-receiver lists can be bypassed via {@code MethodPointerExpression} unless explicitly blocked.</li>
 *   <li>This is a strong compile-time filter, not a JVM SecurityManager sandbox.</li>
 *   <li>We combine it with {@link ScriptSecurityValidator} (pre-parse) and a minimal binding surface.</li>
 * </ul>
 *
 * <p>Test checklist (expected results):
 * <ul>
 *   <li>Allowed: ai.placeBlock(...), for loops, if statements, local variables</li>
 *   <li>Rejected: System.getenv(), Runtime.getRuntime().exec(...), new File(...),
 *       new Socket(...), Class.forName(...), new Thread(...), import java.io.File,
 *       class Evil {}</li>
 * </ul>
 */
public final class SafeGroovyShellFactory {

    private static final Set<String> FORBIDDEN_CONSTRUCTOR_TYPES = Set.of(
        File.class.getName(),
        Socket.class.getName(),
        URL.class.getName(),
        ProcessBuilder.class.getName(),
        Thread.class.getName(),
        Runtime.class.getName(),
        GroovyShell.class.getName(),
        Binding.class.getName(),
        Class.class.getName()
    );

    private static final Set<String> FORBIDDEN_METHOD_NAMES = Set.of(
        "getclass",
        "getenv",
        "getproperties",
        "getruntime",
        "exec",
        "forname",
        "getbinding",
        "evaluate",
        "run",
        "exit"
    );

    private SafeGroovyShellFactory() {
    }

    public static GroovyShell createShell(Binding binding) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(AiBuildScriptBase.class.getName());
        configuration.addCompilationCustomizers(buildSecureAstCustomizer());
        return new GroovyShell(binding, configuration);
    }

    private static SecureASTCustomizer buildSecureAstCustomizer() {
        SecureASTCustomizer secure = new SecureASTCustomizer();

        // Groovy 3.0.x: import rules are whitelist OR blacklist, never both.
        // Empty whitelists block even java.lang.Object and prevent any script from compiling.
        // Use blacklist-only here; explicit "import" lines are already rejected by ScriptSecurityValidator.
        secure.setImportsBlacklist(List.of(
            "java.io",
            "java.net",
            "java.nio",
            "java.lang.reflect",
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Thread",
            "java.lang.Class",
            "groovy.lang.GroovyShell",
            "groovy.lang.Binding",
            "groovy.util.Eval"
        ));
        secure.setIndirectImportCheckEnabled(true);

        // Scripts must be a single compilation unit without package statements.
        secure.setPackageAllowed(false);

        // Prevent user-defined methods (def foo() { ... }) inside the script.
        secure.setMethodDefinitionAllowed(false);

        // Closures enable meta-programming escapes; building scripts only need loops/ifs.
        // The customizer's own switch would also reject classic for loops, because Groovy keeps
        // "for (int i = 0; i < n; i++)" headers as a ClosureListExpression. So closures are
        // blocked in isExpressionAuthorized instead, which lets those loops through.
        secure.setClosuresAllowed(true);

        // Block synchronized blocks at the statement level.
        secure.setStatementsBlacklist(List.of(SynchronizedStatement.class));

        // Block method pointers at the expression level (known SecureASTCustomizer bypass vector).
        secure.setExpressionsBlacklist(List.of(MethodPointerExpression.class));

        // Do not set constantTypesClassesWhiteList: it rejects Object-typed variables such as "ai".
        secure.setReceiversClassesWhiteList(List.of(
            AiScriptFacade.class,
            AiBuildScriptBase.class,
            MethodClosure.class,
            MinecraftAI.class,
            Object.class,
            String.class,
            Integer.class,
            Long.class,
            Double.class,
            Float.class,
            Boolean.class,
            BigDecimal.class,
            Number.class,
            Math.class,
            groovy.lang.IntRange.class,
            groovy.lang.ObjectRange.class,
            // Arrays of plain values (rects[k][0], rects.length): AI scripts use them as tables.
            int[].class, int[][].class, long[].class, double[].class, double[][].class, float[].class,
            boolean[].class, String[].class, String[][].class, Object[].class
        ));

        secure.addExpressionCheckers(SafeGroovyShellFactory::isExpressionAuthorized);
        return secure;
    }

    /**
     * @return {@code true} when the expression may remain in the script AST
     */
    private static boolean isExpressionAuthorized(Expression expression) {
        if (expression instanceof MethodPointerExpression) {
            return false;
        }

        // Real closures and lambdas ({ -> }, x -> y) stay forbidden.
        if (expression instanceof org.codehaus.groovy.ast.expr.ClosureExpression) {
            return false;
        }

        if (expression instanceof ConstructorCallExpression constructorCall) {
            ClassNode type = constructorCall.getType();
            if (type != null && isForbiddenConstructor(type.getName())) {
                return false;
            }
        }

        if (expression instanceof MethodCallExpression methodCall) {
            String methodName = methodCall.getMethodAsString();
            if (methodName != null && isForbiddenMethod(methodName)) {
                return false;
            }
        }

        if (expression instanceof PropertyExpression propertyExpression) {
            String propertyName = propertyExpression.getPropertyAsString();
            if (propertyName != null && isForbiddenProperty(propertyName)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isForbiddenConstructor(String typeName) {
        if (typeName == null) {
            return false;
        }
        String normalized = typeName.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_CONSTRUCTOR_TYPES.contains(typeName)) {
            return true;
        }
        return normalized.startsWith("java.io.")
            || normalized.startsWith("java.net.")
            || normalized.startsWith("java.nio.")
            || normalized.startsWith("java.lang.reflect.");
    }

    private static boolean isForbiddenMethod(String methodName) {
        return FORBIDDEN_METHOD_NAMES.contains(methodName.toLowerCase(Locale.ROOT));
    }

    private static boolean isForbiddenProperty(String propertyName) {
        String normalized = propertyName.toLowerCase(Locale.ROOT);
        return normalized.equals("binding")
            || normalized.equals("metaClass")
            || normalized.equals("class");
    }
}
