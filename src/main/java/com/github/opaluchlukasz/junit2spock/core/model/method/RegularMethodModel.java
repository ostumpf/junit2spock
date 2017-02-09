package com.github.opaluchlukasz.junit2spock.core.model.method;

import org.eclipse.jdt.core.dom.MethodDeclaration;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.github.opaluchlukasz.junit2spock.core.model.method.MethodDeclarationHelper.isPrivate;
import static java.util.Optional.empty;

public class RegularMethodModel extends MethodModel {

    private final List<Object> body = new LinkedList<>();

    RegularMethodModel(MethodDeclaration methodDeclaration) {
        super(methodDeclaration);
        if (methodDeclaration.getBody() != null && methodDeclaration.getBody().statements() != null) {
            this.body.addAll(methodDeclaration.getBody().statements());
        }
    }

    @Override
    protected String methodSuffix() {
        return "";
    }

    @Override
    protected String getMethodName() {
        return methodDeclaration().getName().toString();
    }

    @Override
    protected List<Object> body() {
        return body;
    }

    @Override
    protected Optional<String> methodModifier() {
        if (isPrivate(methodDeclaration())) {
            return Optional.of("private");
        }
        return empty();
    }
}
