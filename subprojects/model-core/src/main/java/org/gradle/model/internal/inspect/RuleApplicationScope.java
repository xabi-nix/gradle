/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.internal.inspect;

import org.gradle.api.Nullable;
import org.gradle.model.Each;
import org.gradle.model.Path;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.registry.ModelReferenceNode;
import org.gradle.model.internal.registry.ModelRegistry;

import java.lang.annotation.Annotation;
import java.util.List;

public abstract class RuleApplicationScope {

    public static class SelfRuleApplicationScope extends RuleApplicationScope {
        @Override
        protected void configureRuleActionInternal(ModelRegistry registry, ModelPath scope, ModelActionRole role, ModelAction action) {
            registry.configure(role, action);
        }
    }

    public static class DescendantSubjectRuleApplicationScope extends RuleApplicationScope {
        @Override
        protected void configureRuleActionInternal(ModelRegistry registry, ModelPath scope, ModelActionRole role, ModelAction action) {
            registry.configureMatching(new NonReferenceDescendantsSpec(scope), role, action);
        }
    }

    public static class DescendantInputRuleApplicationScope extends RuleApplicationScope {
        private final int inputIndex;

        public DescendantInputRuleApplicationScope(int inputIndex) {
            this.inputIndex = inputIndex;
        }

        @Override
        protected void configureRuleActionInternal(ModelRegistry registry, ModelPath scope, ModelActionRole role, ModelAction action) {
            registry.configureMatchingInput(new NonReferenceDescendantsSpec(scope), role, action, inputIndex);
        }
    }

    public void configureRuleAction(MethodModelRuleApplicationContext context, ModelActionRole role, MethodRuleAction ruleAction) {
        configureRuleActionInternal(context.getRegistry(), context.getScope(), role, context.contextualize(ruleAction));
    }

    protected abstract void configureRuleActionInternal(ModelRegistry registry, ModelPath scope, ModelActionRole role, ModelAction action);

    private static class NonReferenceDescendantsSpec extends ModelSpec {
        private final ModelPath scope;

        private NonReferenceDescendantsSpec(ModelPath scope) {
            this.scope = scope;
        }

        @Nullable
        @Override
        public ModelPath getAncestor() {
            return scope;
        }

        @Override
        public boolean matches(MutableModelNode node) {
            return !(node instanceof ModelReferenceNode);
        }
    };

    /**
     * Detects if the subject of the rule has been annotated with {@literal @}{@link Each}.
     *
     * @throws IndexOutOfBoundsException If the rule definition has too few parameters.
     */
    public static RuleApplicationScope fromRuleDefinition(RuleSourceValidationProblemCollector problems, MethodRuleDefinition<?, ?> ruleDefinition, int subjectParamIndex) {
        List<List<Annotation>> parameterAnnotations = ruleDefinition.getParameterAnnotations();
        if (subjectParamIndex >= parameterAnnotations.size()) {
            throw new IndexOutOfBoundsException(String.format("Rule definition should have at least %d parameters", subjectParamIndex + 1));
        }

        int annotatedParamIndex = -1;
        for (int paramIndex = 0; paramIndex < parameterAnnotations.size(); paramIndex++) {
            List<Annotation> annotations = parameterAnnotations.get(paramIndex);
            boolean annotatedWithEach = hasAnnotation(annotations, Each.class);
            if (annotatedWithEach) {
                if (annotatedParamIndex != -1) {
                    problems.add(ruleDefinition, "Multiple rule parameters annotated with @Each. Only the subject or one of the inputs should be annotated.");
                }
                if (hasAnnotation(annotations, Path.class)) {
                    problems.add(ruleDefinition, "Rule parameter must not be annotated with both @Path and @Each.");
                }
                annotatedParamIndex = paramIndex;
            }
        }
        if (annotatedParamIndex == -1) {
            return new SelfRuleApplicationScope();
        }
        if (annotatedParamIndex < subjectParamIndex) {
            problems.add(ruleDefinition, "Only the subject or one of the inputs of the rule should be annotated with @Each.");
            annotatedParamIndex = -1;
        }
        if (annotatedParamIndex == subjectParamIndex) {
            return new DescendantSubjectRuleApplicationScope();
        }
        return new DescendantInputRuleApplicationScope(annotatedParamIndex - subjectParamIndex - 1);
    }

    private static boolean hasAnnotation(Iterable<Annotation> annotations, Class<? extends Annotation> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }
}
