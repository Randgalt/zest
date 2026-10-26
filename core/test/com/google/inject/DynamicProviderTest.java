/*
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProvidesMethodBinding;
import com.google.inject.spi.ProvidesMethodTargetVisitor;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamicProviderTest
{
    @Test
    public void testResolvesMethodArguments()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("tangerine");
                                bind(Foo.class).toProvider(new FooProvider());
                            }
                        });

        Foo foo = injector.getInstance(Foo.class);
        assertEquals("tangerine", foo.name);
        assertNotSame(foo.bar, injector.getInstance(Foo.class).bar);
    }

    @Test
    public void testCalledOnEveryProvision()
    {
        CountingProvider provider = new CountingProvider();
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Integer.class).toProvider(provider);
                            }
                        });

        assertEquals(1, (int) injector.getInstance(Integer.class));
        assertEquals(2, (int) injector.getInstance(Integer.class));
        assertEquals(3, (int) injector.getInstance(Key.get(Integer.class)));
    }

    @Test
    public void testScoping()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Integer.class).toProvider(new CountingProvider()).in(Singleton.class);
                            }
                        });

        assertEquals(1, (int) injector.getInstance(Integer.class));
        assertEquals(1, (int) injector.getInstance(Integer.class));
    }

    @Test
    public void testAnnotatedKey()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("clementine");
                                bind(Bar.class).annotatedWith(named("bar")).toProvider(new BarProvider());
                            }
                        });

        assertEquals("clementine", injector.getInstance(Key.get(Bar.class, named("bar"))).name);
    }

    @Test
    public void testPrivateProviderMethod()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("satsuma");
                                bind(Bar.class).toProvider(new PrivateBarProvider());
                            }
                        });

        assertEquals("satsuma", injector.getInstance(Bar.class).name);
    }

    @Test
    public void testProviderArgument()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).toInstance("provider");
                                bind(Bar.class).toProvider(new ProviderArgumentProvider());
                            }
                        });

        assertEquals("from provider", injector.getInstance(Bar.class).name);
    }

    @Test
    public void testDependenciesAreExposedToTheSpi()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("pomelo");
                                bind(Foo.class).toProvider(new FooProvider());
                            }
                        });

        ProviderInstanceBinding<?> binding =
                assertInstanceOf(ProviderInstanceBinding.class, injector.getBinding(Foo.class));
        HasDependencies hasDependencies =
                assertInstanceOf(HasDependencies.class, binding.getUserSuppliedProvider());
        assertEquals(
                Set.of(Key.get(String.class, named("name")), Key.get(Bar.class)),
                hasDependencies.getDependencies().stream().map(Dependency::getKey).collect(toSet()));
    }

    @Test
    public void testExposedToTheSpiAsAProvidesMethodBinding()
    {
        BarProvider provider = new BarProvider();
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("pomelo");
                                bind(Bar.class).annotatedWith(named("bar")).toProvider(provider);
                            }
                        });

        ProvidesMethodBinding<?> binding =
                injector.getBinding(Key.get(Bar.class, named("bar"))).acceptTargetVisitor(new ProvidesMethodVisitor());
        assertEquals(Key.get(Bar.class, named("bar")), binding.getKey());
        assertSame(provider, binding.getEnclosingInstance());
        assertEquals("create", binding.getMethod().getName());
    }

    @Test
    public void testMissingDependencyFailsAtInjectorCreation()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Foo.class).toProvider(new FooProvider());
                                            }
                                        }));

        assertContains(
                exception.getMessage(), "No implementation for String annotated with @Named(\"name\")");
    }

    @Test
    public void testMultibinder()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("yuzu");
                                Multibinder.newSetBinder(binder(), Bar.class)
                                        .addBinding()
                                        .toProvider(new BarProvider());
                            }
                        });

        Set<Bar> bars = injector.getInstance(new Key<Set<Bar>>() {});
        assertEquals(1, bars.size());
        assertEquals("yuzu", bars.iterator().next().name);
    }

    @Test
    public void testExceptionInMethod()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Bar.class).toProvider(new ThrowingProvider());
                            }
                        });

        ProvisionException exception =
                assertThrows(ProvisionException.class, () -> injector.getInstance(Bar.class));
        assertContains(exception.getMessage(), "ErrorInCustomProvider", "boom");
    }

    @Test
    public void testNullIsRejectedForNonNullableDependency()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Bar.class).toProvider(new NullProvider());
                            }
                        });

        ProvisionException exception =
                assertThrows(ProvisionException.class, () -> injector.getInstance(BarHolder.class));
        assertContains(exception.getMessage(), "null returned by binding at", "is not @Nullable");
        assertNull(injector.getInstance(NullableBarHolder.class).bar);
    }

    @Test
    public void testGetIsUnsupported()
    {
        UnsupportedOperationException exception =
                assertThrows(UnsupportedOperationException.class, () -> new BarProvider().get());
        assertContains(
                exception.getMessage(),
                BarProvider.class.getName(),
                "bind the instance with toProvider()");
    }

    @Test
    public void testImplementedGetIsRejected()
    {
        assertContains(
                creationErrorFor(new GetImplementedProvider()),
                "implements get(), which is never called for a DynamicProvider");
    }

    @Test
    public void testGetImplementedToThrowSomethingElseIsRejected()
    {
        assertContains(
                creationErrorFor(new ThrowingGetProvider()),
                "implements get(), which is never called for a DynamicProvider");
    }

    @Test
    public void testGetInheritedFromAnInterfaceIsRejected()
    {
        assertContains(
                creationErrorFor(new InheritedGetProvider()),
                "implements get(), which is never called for a DynamicProvider");
    }

    @Test
    public void testGetOverriddenToThrowUnsupportedIsIndistinguishableFromTheDefault()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Bar.class).toProvider(new UnsupportedGetProvider());
                            }
                        });

        assertEquals("default", injector.getInstance(Bar.class).name);
    }

    @Test
    public void testCannotBeBoundByType()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Bar.class).toProvider(BarProvider.class);
                                            }
                                        }));

        assertContains(exception.getMessage(), "must be bound as an instance");
    }

    @Test
    public void testCannotBeBoundByKey()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Bar.class).toProvider(Key.get(BarProvider.class));
                                            }
                                        }));

        assertContains(exception.getMessage(), "must be bound as an instance");
    }

    @Test
    public void testNoProviderMethod()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Bar.class).toProvider(new NoMethodProvider());
                                            }
                                        }));

        assertContains(exception.getMessage(), "must have exactly one @Provides method, found 0");
    }

    @Test
    public void testTwoProviderMethods()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Bar.class).toProvider(new TwoMethodProvider());
                                            }
                                        }));

        assertContains(exception.getMessage(), "must have exactly one @Provides method, found 2");
    }

    @Test
    public void testWrongReturnType()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Foo.class).toProvider(new WrongReturnTypeProvider());
                                            }
                                        }));

        assertContains(exception.getMessage(), "which is not assignable to");
    }

    @Test
    public void testVoidProviderMethodIsRejected()
    {
        assertContains(creationErrorFor(new VoidProvider()), "returns void, which is not assignable to");
    }

    @Test
    public void testBindingAnnotationOnProviderMethodIsRejected()
    {
        assertContains(
                creationErrorFor(new BindingAnnotatedProvider()),
                "has a binding annotation, which a DynamicProvider would ignore");
    }

    @Test
    public void testScopeAnnotationOnProviderMethodIsRejected()
    {
        assertContains(
                creationErrorFor(new ScopeAnnotatedProvider()),
                "has a scope annotation, which a DynamicProvider would ignore");
    }

    @Test
    public void testRejectedInstanceReportsOnlyItsOwnError()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Foo.class).toProvider(new NoFooMethodProvider());
                                            }
                                        }));

        assertEquals(1, exception.getErrorMessages().size(), exception.getMessage());
        assertContains(exception.getMessage(), "must have exactly one @Provides method, found 0");
    }

    @Test
    public void testRejectedTypeReportsOnlyItsOwnError()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Foo.class).toProvider(FooProvider.class);
                                            }
                                        }));

        assertEquals(1, exception.getErrorMessages().size(), exception.getMessage());
        assertContains(exception.getMessage(), "must be bound as an instance");
    }

    @Test
    public void testInjectsFieldsAndVoidMethods()
    {
        MemberInjectedProvider provider = new MemberInjectedProvider();
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("bergamot");
                                bind(Bar.class).toProvider(provider);
                            }
                        });

        assertEquals("bergamot", injector.getInstance(Bar.class).name);
        assertEquals("bergamot", provider.field);
        assertNotNull(provider.fromSetter);
    }

    @Test
    public void testMembersAreInjectedOnce()
    {
        CountingMemberProvider provider = new CountingMemberProvider();
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Bar.class).toProvider(provider);
                            }
                        });

        injector.getInstance(Bar.class);
        injector.getInstance(Bar.class);
        injector.getInstance(Bar.class);

        assertEquals(1, provider.setterCalls);
        assertEquals(3, provider.providerMethodCalls);
    }

    @Test
    public void testMembersAreInjectedAtInjectorCreation()
    {
        CountingMemberProvider provider = new CountingMemberProvider();
        Guice.createInjector(
                new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        bind(Bar.class).toProvider(provider);
                    }
                });

        assertEquals(1, provider.setterCalls);
        assertEquals(0, provider.providerMethodCalls);
    }

    @Test
    public void testMissingMemberBindingFailsAtInjectorCreation()
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Bar.class).toProvider(new MissingMemberProvider());
                                            }
                                        }));

        assertContains(exception.getMessage(), "No implementation for Runnable");
    }

    @Test
    public void testMembersAreInjectedBeforeAnEarlierInstanceBindingUsesTheProvider()
    {
        assertEquals("members injected", nameSeenByInstanceBinding(true));
    }

    @Test
    public void testMembersAreInjectedBeforeALaterInstanceBindingUsesTheProvider()
    {
        assertEquals("members injected", nameSeenByInstanceBinding(false));
    }

    private static String nameSeenByInstanceBinding(boolean instanceFirst)
    {
        BarSetter barSetter = new BarSetter();
        Guice.createInjector(
                new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        if (instanceFirst) {
                            bind(BarSetter.class).toInstance(barSetter);
                        }
                        bind(Bar.class).toProvider(new MemberUsingProvider());
                        if (!instanceFirst) {
                            bind(BarSetter.class).toInstance(barSetter);
                        }
                    }
                });
        return barSetter.bar.name;
    }

    @Test
    public void testInjectionPointsAreExposedToTheSpi()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Bar.class).toProvider(new CountingMemberProvider());
                            }
                        });

        ProviderInstanceBinding<?> binding =
                assertInstanceOf(ProviderInstanceBinding.class, injector.getBinding(Bar.class));
        assertEquals(
                Set.of("setDep"),
                binding.getInjectionPoints().stream()
                        .map(injectionPoint -> injectionPoint.getMember().getName())
                        .collect(toSet()));
    }

    @Test
    public void testOptionalMemberInjection()
    {
        OptionalMemberProvider provider = new OptionalMemberProvider();
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(Bar.class).toProvider(provider);
                            }
                        });

        injector.getInstance(Bar.class);
        assertNull(provider.missing);
    }

    @Test
    public void testSubtypeReturnTypeIsAllowed()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("kumquat");
                                bind(Bar.class).toProvider(new SubtypeProvider());
                            }
                        });

        assertTrue(injector.getInstance(Bar.class) instanceof SubBar);
    }

    @Test
    public void testInheritedProviderMethod()
    {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule()
                        {
                            @Override
                            protected void configure()
                            {
                                bind(String.class).annotatedWith(named("name")).toInstance("pomelo");
                                bind(Bar.class).toProvider(new InheritingBarProvider());
                            }
                        });

        assertEquals("pomelo", injector.getInstance(Bar.class).name);
    }

    @Test
    public void testAnnotatedOverrideOfProviderMethodIsRejected()
    {
        assertContains(
                creationErrorFor(new AnnotatedOverrideProvider()),
                "Overriding @Provides methods is not allowed");
    }

    @Test
    public void testUnannotatedOverrideOfProviderMethodIsRejected()
    {
        assertContains(
                creationErrorFor(new UnannotatedOverrideProvider()),
                "Overriding @Provides methods is not allowed");
    }

    @Test
    public void testAbstractProviderMethodIsRejected()
    {
        assertContains(
                creationErrorFor(new ImplementingBarProvider()),
                "@Provides methods may not be abstract: DynamicProviderTest$AbstractMethodBarProvider.create()");
    }

    @Test
    public void testSurvivesElementRewriting()
    {
        Module module =
                new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        bind(String.class).annotatedWith(named("name")).toInstance("citron");
                        bind(Bar.class).toProvider(new BarProvider());
                    }
                };

        Injector injector =
                Guice.createInjector(
                        Modules.override(module)
                                .with(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Foo.class).toProvider(new FooProvider());
                                            }
                                        }));

        assertEquals("citron", injector.getInstance(Bar.class).name);
        assertEquals("citron", injector.getInstance(Foo.class).name);
    }

    @Test
    public void testMembersAreInjectedWhenTheBindingIsApplied()
    {
        Module module =
                new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        bind(String.class).toInstance("replaced");
                        bind(Bar.class).toProvider(new MemberUsingProvider());
                    }
                };

        Injector injector =
                Guice.createInjector(
                        Modules.override(module)
                                .with(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(String.class).toInstance("replacement");
                                            }
                                        }));

        assertEquals("members injected", injector.getInstance(Bar.class).name);
    }

    @Test
    public void testOverriddenBindingDoesNotInjectTheProvider()
    {
        MissingMemberProvider provider = new MissingMemberProvider();
        Module module =
                new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        bind(Bar.class).toProvider(provider);
                    }
                };

        Injector injector =
                Guice.createInjector(
                        Modules.override(module)
                                .with(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Bar.class).toInstance(new Bar("overriding"));
                                            }
                                        }));

        assertEquals("overriding", injector.getInstance(Bar.class).name);
        assertNull(provider.missing);
    }

    @Test
    public void testSameProviderInTwoInjectors()
    {
        BarProvider provider = new BarProvider();
        Module module =
                new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        bind(String.class).annotatedWith(named("name")).toInstance("lime");
                        bind(Bar.class).toProvider(provider);
                    }
                };

        assertEquals("lime", Guice.createInjector(module).getInstance(Bar.class).name);
        assertEquals("lime", Guice.createInjector(module).getInstance(Bar.class).name);
    }

    static class Bar
    {
        final String name;

        Bar()
        {
            this("default");
        }

        Bar(String name)
        {
            this.name = name;
        }
    }

    static class SubBar
            extends Bar
    {
        SubBar(String name)
        {
            super(name);
        }
    }

    static class Foo
    {
        final String name;
        final Bar bar;

        Foo(String name, Bar bar)
        {
            this.name = name;
            this.bar = bar;
        }
    }

    static class BarHolder
    {
        final Bar bar;

        @Inject
        BarHolder(Bar bar)
        {
            this.bar = bar;
        }
    }

    static class NullableBarHolder
    {
        final Bar bar;

        @Inject
        NullableBarHolder(@Nullable Bar bar)
        {
            this.bar = bar;
        }
    }

    static class FooProvider
            implements DynamicProvider<Foo>
    {
        @Provides
        Foo create(@Named("name") String name, Bar bar)
        {
            return new Foo(name, bar);
        }
    }

    static class BarProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        Bar create(@Named("name") String name)
        {
            return new Bar(name);
        }
    }

    static class PrivateBarProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        private Bar create(@Named("name") String name)
        {
            return new Bar(name);
        }
    }

    static class ProviderArgumentProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        Bar create(Provider<String> name)
        {
            return new Bar("from " + name.get());
        }
    }

    static class SubtypeProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        SubBar create(@Named("name") String name)
        {
            return new SubBar(name);
        }
    }

    abstract static class AbstractBarProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        Bar create(@Named("name") String name)
        {
            return new Bar(name);
        }
    }

    static class InheritingBarProvider
            extends AbstractBarProvider {}

    static class OverriddenBarProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        Bar create()
        {
            return new Bar("overridden");
        }
    }

    static class AnnotatedOverrideProvider
            extends OverriddenBarProvider
    {
        @Provides
        @Override
        Bar create()
        {
            return new Bar("override");
        }
    }

    static class UnannotatedOverrideProvider
            extends OverriddenBarProvider
    {
        @Override
        Bar create()
        {
            return new Bar("override");
        }
    }

    abstract static class AbstractMethodBarProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        abstract Bar create(@Named("name") String name);
    }

    static class ImplementingBarProvider
            extends AbstractMethodBarProvider
    {
        @Override
        Bar create(String name)
        {
            return new Bar(name);
        }
    }

    static class CountingProvider
            implements DynamicProvider<Integer>
    {
        private int count;

        @Provides
        Integer next()
        {
            return ++count;
        }
    }

    static class ThrowingProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        Bar create()
        {
            throw new UnsupportedOperationException("boom");
        }
    }

    static class NullProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        Bar create()
        {
            return null;
        }
    }

    static class NoMethodProvider
            implements DynamicProvider<Bar> {}

    private static String creationErrorFor(DynamicProvider<Bar> provider)
    {
        CreationException exception =
                assertThrows(
                        CreationException.class,
                        () ->
                                Guice.createInjector(
                                        new AbstractModule()
                                        {
                                            @Override
                                            protected void configure()
                                            {
                                                bind(Bar.class).toProvider(provider);
                                            }
                                        }));
        return exception.getMessage();
    }

    static class Dep {}

    static class GetImplementedProvider
            implements DynamicProvider<Bar>
    {
        @Override
        public Bar get()
        {
            return new Bar("from get");
        }

        @Provides
        Bar create()
        {
            return new Bar();
        }
    }

    static class ThrowingGetProvider
            implements DynamicProvider<Bar>
    {
        @Override
        public Bar get()
        {
            throw new IllegalStateException("nope");
        }

        @Provides
        Bar create()
        {
            return new Bar();
        }
    }

    interface GetProvidingProvider
            extends DynamicProvider<Bar>
    {
        @Override
        default Bar get()
        {
            return new Bar("from default method");
        }
    }

    static class InheritedGetProvider
            implements GetProvidingProvider
    {
        @Provides
        Bar create()
        {
            return new Bar();
        }
    }

    static class UnsupportedGetProvider
            implements DynamicProvider<Bar>
    {
        @Override
        public Bar get()
        {
            throw new UnsupportedOperationException();
        }

        @Provides
        Bar create()
        {
            return new Bar();
        }
    }

    static class MemberInjectedProvider
            implements DynamicProvider<Bar>
    {
        @Inject
        @Named("name")
        String field;

        Dep fromSetter;

        @Inject
        void setFromSetter(Dep dep)
        {
            this.fromSetter = dep;
        }

        @Provides
        Bar create(@Named("name") String name)
        {
            return new Bar(name);
        }
    }

    static class CountingMemberProvider
            implements DynamicProvider<Bar>
    {
        int setterCalls;
        int providerMethodCalls;

        @Inject
        void setDep(Dep dep)
        {
            setterCalls++;
        }

        @Provides
        Bar create()
        {
            providerMethodCalls++;
            return new Bar();
        }
    }

    static class MissingMemberProvider
            implements DynamicProvider<Bar>
    {
        @Inject
        Runnable missing;

        @Provides
        Bar create()
        {
            return new Bar();
        }
    }

    static class MemberUsingProvider
            implements DynamicProvider<Bar>
    {
        @Inject
        Dep dep;

        @Provides
        Bar create()
        {
            return new Bar(dep == null ? "members not injected" : "members injected");
        }
    }

    static class BarSetter
    {
        Bar bar;

        @Inject
        void setBar(Bar bar)
        {
            this.bar = bar;
        }
    }

    static class NoFooMethodProvider
            implements DynamicProvider<Foo> {}

    static class OptionalMemberProvider
            implements DynamicProvider<Bar>
    {
        @Inject(optional = true)
        Runnable missing;

        @Provides
        Bar create()
        {
            return new Bar();
        }
    }

    static class TwoMethodProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        Bar one()
        {
            return new Bar("one");
        }

        @Provides
        Bar two()
        {
            return new Bar("two");
        }
    }

    static class WrongReturnTypeProvider
            implements DynamicProvider<Foo>
    {
        @Provides
        Bar create()
        {
            return new Bar("wrong");
        }
    }

    static class VoidProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        void create() {}
    }

    static class BindingAnnotatedProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        @Named("ignored")
        Bar create()
        {
            return new Bar();
        }
    }

    static class ScopeAnnotatedProvider
            implements DynamicProvider<Bar>
    {
        @Provides
        @Singleton
        Bar create()
        {
            return new Bar();
        }
    }

    static class ProvidesMethodVisitor
            extends DefaultBindingTargetVisitor<Object, ProvidesMethodBinding<?>>
            implements ProvidesMethodTargetVisitor<Object, ProvidesMethodBinding<?>>
    {
        @Override
        public ProvidesMethodBinding<?> visit(ProvidesMethodBinding<?> binding)
        {
            return binding;
        }
    }

    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface Nullable {}
}
