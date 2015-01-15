/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class InnerRuleSourceIntegrationTest extends AbstractIntegrationSpec {

    def "rule source can be applied in scope of a collection builder element"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MessageTask extends DefaultTask {
                String message = "default"

                @TaskAction
                void printMessages() {
                    println "message: $message"
                }
            }

            @RuleSource
            class EchoRules {
                @Mutate
                void mutateEcho(Task echo, String message) {
                    echo.message = message
                }
            }

            @RuleSource
            class Rules {
                @Model
                String message() {
                    "foo"
                }

                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.create("echo", MessageTask)
                    tasks.named("echo", EchoRules)
                }
            }

            apply type: Rules
        '''

        then:
        succeeds "echo"

        and:
        output.contains "message: foo"
    }

    def "inner rule execution failure yields useful error message"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @RuleSource
            class ThrowingRule {
                @Mutate
                void badRule(Task echo) {
                    throw new RuntimeException("I'm broken")
                }
            }

            @RuleSource
            class Rules {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.named("taskWithThrowingRuleApplied", ThrowingRule)
                    tasks.create("taskWithThrowingRuleApplied")
                }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: ThrowingRule#badRule(org.gradle.api.Task)")
        failure.assertHasCause("I'm broken")
    }

    def "invalid rule definitions of inner rules are reported with a message helping to identify the faulty rule"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @RuleSource
            class InvalidRuleSource {
                @Mutate
                String invalidRule(Task echo) {
                }
            }

            @RuleSource
            class Rules {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.named("taskWithInvalidRuleSourceApplied", InvalidRuleSource)
                    tasks.create("taskWithInvalidRuleSourceApplied")
                }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: Rules#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>)")
        failure.assertHasCause("InvalidRuleSource#invalidRule(org.gradle.api.Task) is not a valid model rule method")
    }

    def "unbound inputs of inner rules are reported and their scope is shown"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @RuleSource
            class UnboundRuleSource {
                @Mutate
                void unboundRule(String string, Integer integer, @Path("some.inner.path") String withInnerPath) {
                }
            }

            @RuleSource
            class Rules {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.named("taskWithUnboundRuleSourceApplied", UnboundRuleSource)
                    tasks.create("taskWithUnboundRuleSourceApplied")
                }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("""The following model rules are unbound:
  UnboundRuleSource#unboundRule(java.lang.String, java.lang.Integer, java.lang.String)
    Mutable:
      - <unspecified> (java.lang.String) parameter 1 in scope of 'tasks.taskWithUnboundRuleSourceApplied'
    Immutable:
      - <unspecified> (java.lang.Integer) parameter 2
      - tasks.taskWithUnboundRuleSourceApplied.some.inner.path (java.lang.String) parameter 3""")
    }
}