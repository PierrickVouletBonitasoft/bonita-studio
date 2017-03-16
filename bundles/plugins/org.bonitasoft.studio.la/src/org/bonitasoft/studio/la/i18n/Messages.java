/**
 * Copyright (C) 2016 Bonitasoft S.A.
 * Bonitasoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.studio.la.i18n;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

    public static String url;
    public static String applicationStoreName;
    public static String createNewApplicationDescriptor;
    public static String create;
    public static String newApplicationDescriptorTitle;
    public static String newApplicationDescription;
    public static String applicationToken;
    public static String applicationTokenMessage;
    public static String version;
    public static String displayName;
    public static String displayNameMessage;
    public static String required;
    public static String tokenValidatorMessage;
    public static String source;
    public static String deployingLivingApplication;
    public static String deletingApplication;
    public static String open;
    public static String openExistingApplication;
    public static String openExistingApplicationDescription;
    public static String appNameUniqueness;
    public static String deleteExistingApplication;
    public static String deleteExistingApplicationDescription;
    public static String deleteConfirmation;
    public static String deleteConfirmationMessage;
    public static String deleteSingleConfirmationMessage;
    public static String deleteSingleDoneMessage;
    public static String deleteDoneTitle;
    public static String deleteDoneMessage;
    public static String deployDoneTitle;
    public static String deployDoneMessage;
    public static String deployFailedTitle;
    public static String deploy;
    public static String fetchFromDatabase;
    public static String delete;
    public static String overview;
    public static String description;
    public static String profile;
    public static String saveBeforeDeploy;
    public static String saveBeforeDeployTitle;
    public static String layout;
    public static String theme;
    public static String versionMessage;
    public static String descriptionMessage;
    public static String themeMessage;
    public static String customProfile;

    static {
        NLS.initializeMessages("messages", Messages.class);
    }
}