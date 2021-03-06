/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.lib.security.http;

import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.server.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SSOAppAuthenticator extends AbstractSSOAuthenticator {
  private static final Logger LOG = LoggerFactory.getLogger(SSOAppAuthenticator.class);

  public SSOAppAuthenticator(SSOService ssoService) {
    super(ssoService);
  }

  @Override
  protected Logger getLog() {
    return LOG;
  }

  String getAppAuthToken(HttpServletRequest req) {
    return req.getHeader(SSOConstants.X_APP_AUTH_TOKEN);
  }

  String getAppComponentId(HttpServletRequest req) {
    return req.getHeader(SSOConstants.X_APP_COMPONENT_ID);
  }

  @Override
  public Authentication validateRequest(ServletRequest request, ServletResponse response, boolean mandatory)
      throws ServerAuthException {
    HttpServletRequest httpReq = (HttpServletRequest) request;
    HttpServletResponse httpRes = (HttpServletResponse) response;
    Authentication ret;
    String componentId = getAppComponentId(httpReq);
    if (!mandatory) {
      if (LOG.isDebugEnabled()) {
        LOG.trace("URL '{}' does not require authentication", getRequestInfoForLogging(httpReq, componentId));
      }
      ret = Authentication.NOT_CHECKED;
    } else {
      if (((HttpServletRequest) request).getHeader(SSOConstants.X_REST_CALL) == null) {
        ret = returnForbidden(httpReq, httpRes, componentId, "Not a REST call: {}");
      } else {
        String authToken = getAppAuthToken(httpReq);
        if (authToken == null) {
          ret = returnForbidden(httpReq, httpRes, componentId, "Missing App authentication token: {}");
        } else if (componentId == null) {
          ret = returnForbidden(httpReq, httpRes, null, "Missing App ID: {}");
        } else {
          getSsoService().refresh();
          SSOUserPrincipal principal = getSsoService().validateAppToken(authToken, componentId);
          if (principal != null) {
            ret = new SSOAuthenticationUser(principal);
          } else {
            ret = returnForbidden(httpReq, httpRes, componentId, "Invalid App token: {}");
          }
        }
      }
    }
    return ret;
  }

}
