/******************************************************************************
 *   Copyright (c) 2014 VMware, Inc. All Rights Reserved.
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *****************************************************************************/
package com.vmware.bdd.usermgmt.i18n;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Created By xiaoliangl on 12/18/14.
 */
public class Messages {
   private static final String BUNDLE_NAME = "com.vmware.bdd.usermgmt.i18n.messages"; //$NON-NLS-1$

   private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

   private Messages() {
   }

   public static String getString(String key, Object... details) {
      String msg = null;
      try {
         msg = RESOURCE_BUNDLE.getString(key);
      } catch (MissingResourceException e) {
         return '!' + key + '!';
      }

      if(details != null) {
         return String.format(msg, details);
      }

      return msg;
   }
}
