package com.google.inject;

/**
 * A module whose bindings are all singletons. It's given a {@link SingletonBinder} from
 * {@link Binder#asSingleton()} instead of a {@link Binder}, so every binding it creates is in
 * singleton scope, as if each ended with {@code in(Scopes.SINGLETON)}, and there's no unscoped
 * {@code bind()} to call by mistake:
 *
 * <pre>
 * public class ServiceModule implements SingletonModule {
 *   public void configure(SingletonBinder singletons) {
 *     singletons.bind(Service.class).to(ServiceImpl.class);
 *     singletons.eagerly().bind(Cache.class);
 *   }
 * }</pre>
 *
 * <p>It can also be written as a lambda:
 *
 * <pre>
 *     SingletonModule module = singletons -> singletons.bind(Service.class).to(ServiceImpl.class);</pre>
 *
 * <p>Its {@link Provides @Provides} methods, and other provider methods such as
 * {@code @ProvidesIntoSet} ones, are singletons too, unless they have a scope annotation of their
 * own. They are never eager singletons, even when the module's other bindings are made eager with
 * {@link SingletonBinder#eagerly()}. Since this module has no {@link Binder}, bind instances and
 * constants, and install other modules, from a regular {@link Module}.
 */
@FunctionalInterface
public interface SingletonModule
        extends Module
{
    /**
     * Calls {@link #configure(SingletonBinder)} with {@code binder.asSingleton()}. Overriding this
     * method removes the scoping.
     */
    @Override
    default void configure(Binder binder)
    {
        configure(binder.asSingleton());
    }

    /**
     * Creates this module's bindings with {@code singletons}.
     */
    void configure(SingletonBinder singletons);
}
