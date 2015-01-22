/**
 * Copyright (C) 2014 Bonitasoft S.A.
 * Bonitasoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.studio.data.provider;

import org.bonitasoft.studio.common.log.BonitaStudioLog;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;



public class JavaQualifiedTypeHelper {

    public static String retrieveQualifiedType(final String typeErasure, final IType declaringType) {
        String qualifiedType = Object.class.getName();
        try {
            qualifiedType = JavaModelUtil.getResolvedTypeName(typeErasure, declaringType);
            qualifiedType = handlePrimitiveTypes(qualifiedType);
        } catch (final JavaModelException e) {
            BonitaStudioLog.error(e);
        } catch (final IllegalArgumentException e) {
            BonitaStudioLog.error(e);
        }
        return qualifiedType;
    }

    protected static String handlePrimitiveTypes(String qualifiedType) {
        if ("int".equals(qualifiedType)) {
            qualifiedType = Integer.class.getName();
        } else if ("boolean".equals(qualifiedType)) {
            qualifiedType = Boolean.class.getName();
        } else if ("long".equals(qualifiedType)) {
            qualifiedType = Long.class.getName();
        } else if ("float".equals(qualifiedType)) {
            qualifiedType = Float.class.getName();
        } else if ("double".equals(qualifiedType)) {
            qualifiedType = Double.class.getName();
        } else if ("short".equals(qualifiedType)) {
            qualifiedType = Short.class.getName();
        } else if ("byte".equals(qualifiedType)) {
            qualifiedType = Byte.class.getName();
        } else if ("E".equals(qualifiedType)) {
            qualifiedType = Object.class.getName();
        } else if ("V".equals(qualifiedType)) {
            qualifiedType = Object.class.getName();
        }
        return qualifiedType;
    }

}
