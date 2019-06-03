/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.internal.protocol.test.InternalDebugOptions;

import java.io.Serializable;

public class DefaultDebugOptions implements InternalDebugOptions, Serializable {

    private int port = -1;
    private boolean suspend = false;

    @Override
    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public boolean isDebugMode() {
        return port > 0;
    }

    @Override
    public boolean isSuspend() {
        return false;
    }

    public void setSuspend(boolean suspend) {
        this.suspend = suspend;
    }
}
