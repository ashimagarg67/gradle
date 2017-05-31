/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeintegration.processenvironment;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.internal.nativeintegration.ImmutableEnvironmentException;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.ReflectiveEnvironment;

import java.io.File;
import java.util.List;
import java.util.Map;

public abstract class AbstractProcessEnvironment implements ProcessEnvironment {
    //for updates to private JDK caches of the environment state
    private final ReflectiveEnvironment reflectiveEnvironment;

    public AbstractProcessEnvironment() {
        this.reflectiveEnvironment = new ReflectiveEnvironment();
    }

    @Override
    public boolean maybeSetEnvironment(Map<String, String> source) {
        try {
            boolean removalsHaveSucceeded = true;
            boolean setingHasSucceeded = true;
            // need to take copy to prevent ConcurrentModificationException
            List<String> keysToRemove = Lists.newArrayList(Sets.difference(System.getenv().keySet(), source.keySet()));
            for (String key : keysToRemove) {
                if (removalsHaveSucceeded) {
                    removalsHaveSucceeded = maybeRemoveEnvironmentVariable(key);
                }
            }
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (setingHasSucceeded) {
                    setingHasSucceeded = maybeSetEnvironmentVariable(entry.getKey(), entry.getValue());
                }
            }
            return removalsHaveSucceeded && setingHasSucceeded;
        } catch (ImmutableEnvironmentException e) {
            return false;
        }
    }

    @Override
    public void removeEnvironmentVariable(String name) throws NativeIntegrationException {
        try {
            removeNativeEnvironmentVariable(name);
            reflectiveEnvironment.unsetenv(name);
        } catch (ImmutableEnvironmentException e) {
            throw new NativeIntegrationException(String.format("Couldn't remove environment variable: %s", name), e);
        }
    }

    protected abstract void removeNativeEnvironmentVariable(String name);

    @Override
    public boolean maybeRemoveEnvironmentVariable(String name) {
        try {
            removeEnvironmentVariable(name);
            return true;
        } catch (NativeIntegrationException e) {
            if (e.getCause() instanceof ImmutableEnvironmentException) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void setEnvironmentVariable(String name, String value) throws NativeIntegrationException {
        if (value == null) {
            removeEnvironmentVariable(name);
            return;
        }

        try {
            setNativeEnvironmentVariable(name, value);
            reflectiveEnvironment.setenv(name, value);
        } catch (ImmutableEnvironmentException e) {
            throw new NativeIntegrationException(String.format("Couldn't set environment variable %s to %s", name, value), e);
        }
    }

    protected abstract void setNativeEnvironmentVariable(String name, String value);

    @Override
    public boolean maybeSetEnvironmentVariable(String name, String value) {
        try {
            setEnvironmentVariable(name, value);
            return true;
        } catch (NativeIntegrationException e) {
            if (e.getCause() instanceof ImmutableEnvironmentException) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void setProcessDir(File processDir) throws NativeIntegrationException {
        if (!processDir.exists()) {
            return;
        }

        setNativeProcessDir(processDir);
        System.setProperty("user.dir", processDir.getAbsolutePath());
    }

    protected abstract void setNativeProcessDir(File processDir);

    @Override
    public boolean maybeSetProcessDir(File processDir) {
        setProcessDir(processDir);
        return true;
    }

    @Override
    public Long maybeGetPid() {
        return getPid();
    }

    @Override
    public boolean maybeDetachProcess() {
        detachProcess();
        return true;
    }
}
