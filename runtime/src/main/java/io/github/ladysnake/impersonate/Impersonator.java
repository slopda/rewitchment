package io.github.ladysnake.impersonate;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public interface Impersonator {
    static Impersonator get(PlayerEntity player) {
        return new ReflectiveBridge(player);
    }

    void impersonate(Identifier reason, GameProfile profile);

    GameProfile stopImpersonation(Identifier reason);

    final class ReflectiveBridge implements Impersonator {
        private static final String MODERN_API = "org.ladysnake.impersonate.Impersonator";

        private final Object delegate;
        private final Class<?> apiClass;

        private ReflectiveBridge(PlayerEntity player) {
            try {
                apiClass = Class.forName(MODERN_API);
                Method get = apiClass.getMethod("get", PlayerEntity.class);
                delegate = get.invoke(null, player);
                if (delegate == null) {
                    throw new IllegalStateException("Modern Impersonate returned null for player");
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "The 1.21.1 Impersonate library must be installed; modern API not found", e);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot access the modern Impersonate API", e);
            }
        }

        @Override
        public void impersonate(Identifier reason, GameProfile profile) {
            invoke("impersonate", new Class<?>[] {Identifier.class, GameProfile.class}, reason, profile);
        }

        @Override
        public GameProfile stopImpersonation(Identifier reason) {
            return (GameProfile) invoke("stopImpersonation", new Class<?>[] {Identifier.class}, reason);
        }

        private Object invoke(String method, Class<?>[] argumentTypes, Object... args) {
            try {
                return apiClass.getMethod(method, argumentTypes).invoke(delegate, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Modern Impersonate invocation failed: " + method, cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Modern Impersonate API mismatch: " + method, e);
            }
        }
    }
}
