/*
 * Copyright (C) 2009 The Android Open Source Project
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

package signature.compare.model.impl;

import java.util.Set;

import signature.compare.model.IAnnotationDelta;
import signature.compare.model.IParameterDelta;
import signature.compare.model.ITypeReferenceDelta;
import signature.model.IParameter;

public class SigParameterDelta extends SigDelta<IParameter> implements
        IParameterDelta {

    private ITypeReferenceDelta<?> typeDelta;
    private Set<IAnnotationDelta> annotationDeltas;

    public SigParameterDelta(IParameter from, IParameter to) {
        super(from, to);
    }

    public ITypeReferenceDelta<?> getTypeDelta() {
        return typeDelta;
    }

    public void setTypeDelta(ITypeReferenceDelta<?> typeDelta) {
        this.typeDelta = typeDelta;
    }

    public Set<IAnnotationDelta> getAnnotationDeltas() {
        return annotationDeltas;
    }

    public void setAnnotationDeltas(Set<IAnnotationDelta> annotationDeltas) {
        this.annotationDeltas = annotationDeltas;
    }
}
