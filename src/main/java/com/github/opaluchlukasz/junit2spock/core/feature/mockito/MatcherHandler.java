package com.github.opaluchlukasz.junit2spock.core.feature.mockito;

import com.github.opaluchlukasz.junit2spock.core.ASTNodeFactory;
import com.github.opaluchlukasz.junit2spock.core.node.GroovyClosure;
import com.github.opaluchlukasz.junit2spock.core.node.GroovyClosureBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.github.opaluchlukasz.junit2spock.core.node.ClosureHelper.asClosure;
import static com.github.opaluchlukasz.junit2spock.core.node.CustomInfixOperator.CAST;
import static com.github.opaluchlukasz.junit2spock.core.node.GroovyClosureBuilder.IT;
import static com.github.opaluchlukasz.junit2spock.core.util.AstNodeFinder.methodInvocation;
import static java.util.Collections.singletonList;
import static org.eclipse.jdt.core.dom.PrefixExpression.Operator.NOT;

@Component
public class MatcherHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MatcherHandler.class);

    private static final Map<String, String> MATCHER_TYPE_OVERRIDE = ImmutableMap.of("Char", "Character", "Int", "Integer");

    private final ASTNodeFactory nodeFactory;
    private final GroovyClosureBuilder groovyClosureBuilder;

    @Autowired
    public MatcherHandler(ASTNodeFactory nodeFactory, GroovyClosureBuilder groovyClosureBuilder) {
        this.nodeFactory = nodeFactory;
        this.groovyClosureBuilder = groovyClosureBuilder;
    }

    ASTNode applyMatchers(Object argument) {
        return methodInvocation(argument).map(methodInvocation -> {
            switch (methodInvocation.getName().getIdentifier()) {
                case "anyVararg":
                case "anyObject":
                    return wildcard();
                case "any":
                    return anyMatcher(methodInvocation);
                case "anyBoolean":
                case "anyByte":
                case "anyChar":
                case "anyInt":
                case "anyLong":
                case "anyFloat":
                case "anyDouble":
                case "anyShort":
                case "anyString":
                case "anyList":
                case "anySet":
                case "anyMap":
                case "anyIterable":
                case "anyCollection":
                    return classMatcher(getClass(methodInvocation));
                case "anyListOf":
                    return classMatcher(parametrizedTypeOf(methodInvocation, List.class));
                case "anySetOf":
                    return classMatcher(parametrizedTypeOf(methodInvocation, Set.class));
                case "anyCollectionOf":
                    return classMatcher(parametrizedTypeOf(methodInvocation, Collection.class));
                case "anyIterableOf":
                    return classMatcher(parametrizedTypeOf(methodInvocation, Iterable.class));
                case "anyMapOf":
                    return classMatcher(anyMapOf(methodInvocation, Map.class));
                case "isA":
                    return anyMatcher(methodInvocation);
                case "isNull":
                    return nodeFactory.nullLiteral();
                case "eq":
                    return eqMatcher(methodInvocation);
                case "isNotNull":
                case "notNull":
                    return nodeFactory.prefixExpression(NOT, nodeFactory.nullLiteral());
                case "contains":
                case "endsWith":
                case "startsWith":
                    return stringMatcher(methodInvocation);
                case "argThat":
                case "booleanThat":
                case "byteThat":
                case "charThat":
                case "doubleThat":
                case "floatThat":
                case "intThat":
                case "longThat":
                case "shortThat":
                    return handleArgThat(methodInvocation);
                default:
                    LOG.warn("Unsupported Mockito matcher: {}", methodInvocation.getName().getIdentifier());
                    return nodeFactory.clone((ASTNode) argument);
            }
        }).orElseGet(() -> nodeFactory.clone((ASTNode) argument));
    }

    private Expression stringMatcher(MethodInvocation methodInvocation) {
        return singleArityMatcher(methodInvocation, argument ->
                groovyClosureBuilder.aClosure()
                        .withBodyStatement(nodeFactory.expressionStatement(
                                nodeFactory.methodInvocation(methodInvocation.getName().getIdentifier(),
                                singletonList(nodeFactory.clone(argument)), nodeFactory.simpleName(IT))))
                        .withTypeLiteral(nodeFactory.typeLiteral(nodeFactory.simpleType(String.class.getSimpleName())))
                        .build()
                        .asExpression());
    }

    private Expression handleArgThat(MethodInvocation methodInvocation) {
        return singleArityMatcher(methodInvocation, argument -> {
            Optional<GroovyClosure> expression = asClosure(nodeFactory, groovyClosureBuilder, argument, "matches");
            return expression.map(GroovyClosure::asExpression).orElse(nodeFactory.clone(methodInvocation));
        });
    }

    SimpleName wildcard() {
        return nodeFactory.simpleName("_");
    }

    private Type parametrizedTypeOf(MethodInvocation methodInvocation, Class<?> clazz) {
        if (methodInvocation.arguments().size() == 1 && methodInvocation.arguments().get(0) instanceof TypeLiteral) {
            return nodeFactory.parameterizedType(nodeFactory.simpleType(clazz.getSimpleName()),
                    singletonList(nodeFactory.clone(((TypeLiteral) methodInvocation.arguments().get(0)).getType())));
        } else {
            return nodeFactory.simpleType(clazz.getSimpleName());
        }
    }

    private Type anyMapOf(MethodInvocation methodInvocation, Class<?> clazz) {
        if (methodInvocation.arguments().size() == 2 &&
                methodInvocation.arguments().get(0) instanceof TypeLiteral &&
                methodInvocation.arguments().get(1) instanceof TypeLiteral) {
            return nodeFactory.parameterizedType(nodeFactory.simpleType(clazz.getSimpleName()),
                    ImmutableList.of(nodeFactory.clone(((TypeLiteral) methodInvocation.arguments().get(0)).getType()),
                    nodeFactory.clone(((TypeLiteral) methodInvocation.arguments().get(1)).getType())));
        } else {
            return nodeFactory.simpleType(clazz.getSimpleName());
        }
    }

    private ASTNode anyMatcher(MethodInvocation methodInvocation) {
        if (methodInvocation.arguments().size() == 1 && methodInvocation.arguments().get(0) instanceof TypeLiteral) {
            return classMatcher(((TypeLiteral) methodInvocation.arguments().get(0)).getType());
        } else {
            return wildcard();
        }
    }

    private Expression singleArityMatcher(MethodInvocation methodInvocation, Function<Expression, Expression> handler) {
        if (methodInvocation.arguments().size() == 1) {
            return handler.apply((Expression) methodInvocation.arguments().get(0));
        }
        LOG.warn("Unsupported {} matcher arity.", methodInvocation.getName());
        return nodeFactory.clone(methodInvocation);
    }

    private ASTNode eqMatcher(MethodInvocation methodInvocation) {
        return singleArityMatcher(methodInvocation, nodeFactory::clone);
    }

    private Type getClass(MethodInvocation methodInv) {
        String type = methodInv.getName().getIdentifier().replaceFirst("any", "");
        return nodeFactory.simpleType(MATCHER_TYPE_OVERRIDE.getOrDefault(type, type));
    }

    private InfixExpression classMatcher(Type type) {
        TypeLiteral classLiteral = nodeFactory.typeLiteral(nodeFactory.clone(type));
        return nodeFactory.infixExpression(CAST, wildcard(), classLiteral);
    }
}
