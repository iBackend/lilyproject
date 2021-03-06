/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.repository.api;

import org.lilyproject.util.ArgumentValidator;

/**
 * A qualified name. A qualified name consists of a namespace and a name.
 *
 * <p>Qualifed names are used within the repository schema, see {@link FieldType}.
 *
 * <p>They allow to re-use existing vocabularies (e.g. dublin core) without name clashes.
 */
public class QName {

    private final String namespace;
    private final String name;

    public QName(String namespace, String name) {
        ArgumentValidator.notNull(namespace, "namespace");
        ArgumentValidator.notNull(name, "name");
        this.namespace = namespace;
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        QName other = (QName) obj;
        if (!name.equals(other.name)) {
            return false;
        }
        if (namespace == null) {
            if (other.namespace != null) {
                return false;
            }
        } else if (!namespace.equals(other.namespace)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "{" + namespace + "}" + name;
    }

    /**
     * Creates a qname based on a string.
     *
     * <p>The format of the string needs to be the same as the string returned by {@link #toString()}:
     * {namespace}name
     */
    public static QName fromString(String qname) throws IllegalArgumentException {
        int indexBracket = qname.indexOf('}');
        if (indexBracket < 1 || !qname.startsWith("{")) {
            throw new IllegalArgumentException("QName string should be of the format {namespace}name");
        }
        return new QName(qname.substring(1,indexBracket), qname.substring(indexBracket + 1));
    }
}
