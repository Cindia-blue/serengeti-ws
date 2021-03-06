/***************************************************************************
 * Copyright (c) 2012-2015 VMware, Inc. All Rights Reserved.
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
 ***************************************************************************/
package com.vmware.bdd.apitypes;

import java.util.Map;

import com.vmware.bdd.security.tls.CertificateInfo;
import com.vmware.bdd.validation.ValidationError;

public class BddErrorMessage {
   private String code;
   private String message;

   private CertificateInfo certInfo;
   private boolean warning = false;
   private Map<String, ValidationError> errors;

   public BddErrorMessage() {

   }

   public BddErrorMessage(String code, String message) {
      super();
      this.code = code;
      this.message = message;
   }

   public String getCode() {
      return code;
   }

   public void setCode(String code) {
      this.code = code;
   }

   public String getMessage() {
      return message;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public CertificateInfo getCertInfo() {
      return certInfo;
   }

   public void setCertInfo(CertificateInfo certInfo) {
      this.certInfo = certInfo;
   }

   public Map<String, ValidationError> getErrors() {
      return errors;
   }

   public void setErrors(Map<String, ValidationError> errors) {
      this.errors = errors;
   }

   @Override
   public String toString() {
      return "BddErrorMessage [code=" + code + ", message=" + message + "]";
   }

   public boolean isWarning() {
      return warning;
   }

   public void setWarning(boolean warning) {
      this.warning = warning;
   }
}
