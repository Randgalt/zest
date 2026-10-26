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

/**
 * An object capable of providing instances of type {@code T} through a method whose signature is
 * chosen by the implementation rather than fixed by this interface.
 *
 * <p>Where {@link Provider} declares {@code T get()} and must therefore obtain its own dependencies
 * by other means, a {@code DynamicProvider} declares exactly one {@literal @}{@link Provides} method,
 * whose parameters are injected the same way as those of a module's {@literal @}Provides method:
 *
 * <pre>{@code
 * class TransactionProvider
 *         implements DynamicProvider<Transaction>
 * {
 *     @Provides
 *     Transaction create(Connection connection, @Named("isolation") int isolation)
 *     {
 *         return connection.beginTransaction(isolation);
 *     }
 * }
 *
 * bind(Transaction.class).toProvider(new TransactionProvider());
 * }</pre>
 *
 * <p>The provider method may have any name and any access modifier, and its return type must be
 * assignable to the bound type. Its parameters are resolved from the injector on every provision,
 * so missing bindings are reported when the injector is created, not when the first instance is
 * requested. It follows the rules for a module's {@literal @}Provides methods, so it may not be
 * overridden. The binding supplies the key and the scope, so the method may not have a binding
 * annotation or a scope annotation: use {@code annotatedWith()} and {@code in()} on the binding
 * instead.
 *
 * <p>The implementation's {@link Inject} annotated fields and methods are injected like those of any
 * other provider instance bound with {@code toProvider}: when the injector is created, and in any
 * case before the provider method is first called. They may be used to give the provider its own
 * collaborators:
 *
 * <pre>{@code
 * class TransactionProvider
 *         implements DynamicProvider<Transaction>
 * {
 *     @Inject
 *     private Metrics metrics;
 *
 *     @Provides
 *     Transaction create(Connection connection) { ... }
 * }
 * }</pre>
 *
 * <p>Binding a {@code DynamicProvider} binds its provider method the way a module's
 * {@literal @}Provides method is bound, so {@link #get()} is never called. It throws
 * {@link UnsupportedOperationException}, and a {@code DynamicProvider} must be bound as an instance
 * rather than by type. Implementing {@code get()} is an error, reported when the instance is bound:
 * an implementation that both provides from {@code get()} and declares a provider method would
 * silently use only the latter, so such a class should implement {@link Provider} instead.
 *
 * @param <T> the type of object this provides
 * @see com.google.inject.binder.LinkedBindingBuilder#toProvider(Provider)
 */
public interface DynamicProvider<T>
        extends Provider<T>
{
    @Override
    default T get()
    {
        throw new UnsupportedOperationException(
                getClass().getName() + " is a DynamicProvider; bind the instance with toProvider() so its"
                        + " @Provides method is used");
    }
}
