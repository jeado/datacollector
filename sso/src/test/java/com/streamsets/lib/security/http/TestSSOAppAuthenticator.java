/**
 * Copyright 2016 StreamSets Inc.
 * <p/>
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.lib.security.http;

import com.google.common.collect.ImmutableList;
import org.eclipse.jetty.server.Authentication;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

public class TestSSOAppAuthenticator {

  @Test
  public void testConstructor() {
    SSOService ssoService = Mockito.mock(SSOService.class);
    new SSOAppAuthenticator("a", ssoService);
    Mockito.verify(ssoService).setListener(Mockito.<SSOService.Listener>any());
  }

  @Test
  public void testCache() {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    SSOAuthenticationUser user = Mockito.mock(SSOAuthenticationUser.class);

    Assert.assertNull(authenticator.getUserFromCache("authToken"));
    authenticator.cacheToken("authToken", user);
    Assert.assertEquals(user, authenticator.getUserFromCache("authToken"));
    authenticator.invalidateToken("authToken");
    Assert.assertNull(authenticator.getUserFromCache("authToken"));
  }

  @Test
  public void testInvalidate() {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    authenticator.invalidate(ImmutableList.of("a"));
    Mockito.verify(authenticator).invalidateToken(Mockito.eq("a"));
  }

  @Test
  public void testReturnForbidden() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);

    Mockito.doReturn("").when(authenticator).getRequestInfoForLogging(Mockito.eq(req));

    StringWriter stringwriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringwriter);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Mockito.when(res.getWriter()).thenReturn(writer);

    Authentication auth = authenticator.returnForbidden(req, res, "msg");
    Assert.assertEquals(Authentication.SEND_FAILURE, auth);
    Mockito.verify(res).sendError(Mockito.eq(HttpServletResponse.SC_FORBIDDEN));
    Mockito.verify(res).setContentType(Mockito.eq("application/json"));
    Mockito.verify(res).sendError(Mockito.eq(HttpServletResponse.SC_FORBIDDEN));
    writer.close();
    Assert.assertEquals(SSOAppAuthenticator.FORBIDDEN_JSON_STR, stringwriter.toString().trim());
  }

  @Test
  public void testRevalidateTokenNoRevalidation() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    Mockito.doReturn(10000L).when(ssoService).getValidateAppTokenFrequency();
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    SSOUserPrincipal principal = TestSSOUserPrincipalJson.createPrincipal();
    SSOAuthenticationUser user = Mockito.mock(SSOAuthenticationUser.class);
    Mockito.when(user.getSSOUserPrincipal()).thenReturn(principal);
    Mockito.when(user.getValidationTime()).thenReturn(System.currentTimeMillis());

    Authentication got = authenticator.revalidateToken(user, null, null);

    Assert.assertSame(user, got);
  }

  @Test
  public void testRevalidateTokenRevalidatedOK() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    Mockito.doReturn(1L).when(ssoService).getValidateAppTokenFrequency();
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    SSOUserPrincipal principal = TestSSOUserPrincipalJson.createPrincipal();
    SSOAuthenticationUser user = Mockito.mock(SSOAuthenticationUser.class);
    Mockito.when(user.getSSOUserPrincipal()).thenReturn(principal);
    Mockito.when(user.getValidationTime()).thenReturn(0L);

    SSOUserPrincipal validatedUser = Mockito.mock(SSOUserPrincipal.class);
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Mockito
        .doReturn(validatedUser)
        .when(ssoService)
        .validateAppToken(Mockito.eq(principal.getTokenStr()), Mockito.eq(principal.getPrincipalId()));


    Authentication got = authenticator.revalidateToken(user, req, res);
    Mockito
        .verify(authenticator)
        .cacheToken(Mockito.eq(principal.getTokenStr()), Mockito.any(SSOAuthenticationUser.class));

    Assert.assertEquals(validatedUser, ((SSOAuthenticationUser) got).getSSOUserPrincipal());
  }

  @Test
  public void testRevalidateTokenRevalidatedFailed() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    Mockito.doReturn(1L).when(ssoService).getValidateAppTokenFrequency();
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    SSOUserPrincipal principal = TestSSOUserPrincipalJson.createPrincipal();
    SSOAuthenticationUser user = Mockito.mock(SSOAuthenticationUser.class);
    Mockito.when(user.getSSOUserPrincipal()).thenReturn(principal);
    Mockito.when(user.getValidationTime()).thenReturn(0L);

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Authentication forbidden = Mockito.mock(Authentication.class);
    Mockito
        .doReturn(forbidden)
        .when(authenticator)
        .returnForbidden(Mockito.eq(req), Mockito.eq(res), Mockito.anyString());
    Mockito
        .doReturn(null)
        .when(ssoService)
        .validateAppToken(Mockito.eq(principal.getTokenStr()), Mockito.eq(principal.getPrincipalId()));

    Authentication got = authenticator.revalidateToken(user, req, res);
    Mockito.verify(authenticator).invalidateToken(Mockito.eq(principal.getTokenStr()));
    Assert.assertEquals(forbidden, got);
  }

  @Test
  public void testValidateTokenOK() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    SSOUserPrincipal principal = TestSSOUserPrincipalJson.createPrincipal();
    String authToken = principal.getTokenStr();
    String componentId = principal.getPrincipalId();
    Mockito.doReturn(principal).when(ssoService).validateAppToken(Mockito.eq(authToken), Mockito.eq(componentId));


    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

    Authentication got = authenticator.validateToken(authToken, componentId, req, res);
    Mockito.verify(authenticator).cacheToken(Mockito.eq(authToken), Mockito.any(SSOAuthenticationUser.class));

    Assert.assertEquals(principal, ((SSOAuthenticationUser) got).getSSOUserPrincipal());
  }


  @Test
  public void testValidateTokenNoPrincipal() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    String authToken = "authToken";
    String componentId = "componentId";
    Mockito.doReturn(null).when(ssoService).validateAppToken(Mockito.eq(authToken), Mockito.eq(componentId));

    Authentication forbidden = Mockito.mock(Authentication.class);
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Mockito
        .doReturn(forbidden)
        .when(authenticator)
        .returnForbidden(Mockito.eq(req), Mockito.eq(res), Mockito.anyString());

    Authentication got = authenticator.validateToken(authToken, componentId, req, res);

    Assert.assertEquals(forbidden, got);
  }

  @Test
  public void testValidateTokenComponentIdAndPrincipalIdDontMatch() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    SSOUserPrincipal principal = TestSSOUserPrincipalJson.createPrincipal();
    String authToken = principal.getTokenStr();
    String componentId = principal.getPrincipalId();
    Mockito.doReturn(principal).when(ssoService).validateAppToken(Mockito.eq(authToken), Mockito.eq(componentId + "1"));

    Authentication forbidden = Mockito.mock(Authentication.class);
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Mockito
        .doReturn(forbidden)
        .when(authenticator)
        .returnForbidden(Mockito.eq(req), Mockito.eq(res), Mockito.anyString());

    Authentication got = authenticator.validateToken(authToken, componentId + "1", req, res);

    Assert.assertEquals(forbidden, got);
  }

  @Test
  public void testGetAppAuthToken() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_AUTH_TOKEN))).thenReturn("H");
    Assert.assertEquals("H", authenticator.getAppAuthToken(req));
  }


  @Test
  public void testGetAppComponentId() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_COMPONENT_ID))).thenReturn("H");
    Assert.assertEquals("H", authenticator.getAppComponentId(req));
  }

  @Test
  public void testValidateRequestNoRestCall() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Authentication forbidden = Mockito.mock(Authentication.class);
    Mockito
        .doReturn(forbidden)
        .when(authenticator)
        .returnForbidden(Mockito.eq(req), Mockito.eq(res), Mockito.anyString());

    Authentication got = authenticator.validateRequest(req, res, true);
    Mockito.verify(authenticator, Mockito.never()).getAppAuthToken(Mockito.eq(req));
    Assert.assertEquals(forbidden, got);
  }

  @Test
  public void testValidateRequestNoAuthToken() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Authentication forbidden = Mockito.mock(Authentication.class);
    Mockito
        .doReturn(forbidden)
        .when(authenticator)
        .returnForbidden(Mockito.eq(req), Mockito.eq(res), Mockito.anyString());

    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_REST_CALL))).thenReturn("-");
    Authentication got = authenticator.validateRequest(req, res, true);
    Mockito.verify(authenticator).getAppAuthToken(Mockito.eq(req));
    Mockito.verify(authenticator).getAppComponentId(Mockito.eq(req));
    Assert.assertEquals(forbidden, got);
  }

  @Test
  public void testValidateRequestNoComponentId() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Authentication forbidden = Mockito.mock(Authentication.class);
    Mockito
        .doReturn(forbidden)
        .when(authenticator)
        .returnForbidden(Mockito.eq(req), Mockito.eq(res), Mockito.anyString());

    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_REST_CALL))).thenReturn("-");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_AUTH_TOKEN))).thenReturn("authToken");
    Authentication got = authenticator.validateRequest(req, res, true);
    Mockito.verify(authenticator).getAppAuthToken(Mockito.eq(req));
    Mockito.verify(authenticator).getAppComponentId(Mockito.eq(req));
    Assert.assertEquals(forbidden, got);
  }

  @Test
  public void testValidateRequestUserCachedMismatchComponentId() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
    Authentication forbidden = Mockito.mock(Authentication.class);
    Mockito
        .doReturn(forbidden)
        .when(authenticator)
        .returnForbidden(Mockito.eq(req), Mockito.eq(res), Mockito.anyString());

    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_REST_CALL))).thenReturn("-");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_AUTH_TOKEN))).thenReturn("authToken");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_COMPONENT_ID))).thenReturn("componentId");

    SSOUserPrincipal principal = Mockito.mock(SSOUserPrincipal.class);
    SSOAuthenticationUser user = Mockito.mock(SSOAuthenticationUser.class);
    Mockito.when(principal.getPrincipalId()).thenReturn("other");
    Mockito.when(user.getSSOUserPrincipal()).thenReturn(principal);
    Mockito.doReturn(user).when(authenticator).getUserFromCache(Mockito.eq("authToken"));


    Authentication got = authenticator.validateRequest(req, res, true);
    Mockito.verify(ssoService).refresh();
    Mockito.verify(authenticator).getAppAuthToken(Mockito.eq(req));
    Mockito.verify(authenticator).getAppComponentId(Mockito.eq(req));
    Mockito.verify(authenticator).getUserFromCache(Mockito.eq("authToken"));
    Assert.assertEquals(forbidden, got);
  }

  @Test
  public void testValidateRequesUserCachedOK() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    Mockito.doReturn("").when(authenticator).getRequestInfoForLogging(Mockito.any(HttpServletRequest.class));

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_REST_CALL))).thenReturn("-");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_AUTH_TOKEN))).thenReturn("authToken");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_COMPONENT_ID))).thenReturn("componentId");

    SSOUserPrincipal principal = Mockito.mock(SSOUserPrincipal.class);
    SSOAuthenticationUser user = Mockito.mock(SSOAuthenticationUser.class);
    Mockito.when(principal.getPrincipalId()).thenReturn("componentId");
    Mockito.when(user.getSSOUserPrincipal()).thenReturn(principal);
    Mockito.doReturn(user).when(authenticator).getUserFromCache(Mockito.eq("authToken"));

    Mockito.doReturn(user).when(authenticator).revalidateToken(Mockito.eq(user), Mockito.eq(req), Mockito.eq(res));

    Authentication got = authenticator.validateRequest(req, res, true);
    Mockito.verify(ssoService).refresh();
    Mockito.verify(authenticator).getAppAuthToken(Mockito.eq(req));
    Mockito.verify(authenticator).getAppComponentId(Mockito.eq(req));
    Mockito.verify(authenticator).getUserFromCache(Mockito.eq("authToken"));
    Mockito.verify(authenticator).revalidateToken(Mockito.eq(user), Mockito.eq(req), Mockito.eq(res));
    Assert.assertEquals(user, got);
  }

  @Test
  public void testValidateRequesNoUserCachedOK() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    Mockito.doReturn("").when(authenticator).getRequestInfoForLogging(Mockito.any(HttpServletRequest.class));

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_REST_CALL))).thenReturn("-");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_AUTH_TOKEN))).thenReturn("authToken");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_COMPONENT_ID))).thenReturn("componentId");

    Mockito.doReturn(null).when(authenticator).getUserFromCache(Mockito.eq("authToken"));

    SSOAuthenticationUser user = Mockito.mock(SSOAuthenticationUser.class);
    Mockito
        .doReturn(user)
        .when(authenticator)
        .validateToken(Mockito.eq("authToken"), Mockito.eq("componentId"), Mockito.eq(req), Mockito.eq(res));

    Mockito.doReturn(user).when(authenticator).revalidateToken(Mockito.eq(user), Mockito.eq(req), Mockito.eq(res));

    Authentication got = authenticator.validateRequest(req, res, true);
    Mockito.verify(ssoService).refresh();
    Mockito.verify(authenticator).getAppAuthToken(Mockito.eq(req));
    Mockito.verify(authenticator).getAppComponentId(Mockito.eq(req));
    Mockito.verify(authenticator).getUserFromCache(Mockito.eq("authToken"));
    Mockito
        .verify(authenticator)
        .validateToken(Mockito.eq("authToken"), Mockito.eq("componentId"), Mockito.eq(req), Mockito.eq(res));
    Assert.assertEquals(user, got);
  }

  @Test
  public void testValidateRequesNoUserCachedNotMandatory() throws Exception {
    SSOService ssoService = Mockito.mock(SSOService.class);
    SSOAppAuthenticator authenticator = Mockito.spy(new SSOAppAuthenticator("a", ssoService));

    Mockito.doReturn("").when(authenticator).getRequestInfoForLogging(Mockito.any(HttpServletRequest.class));

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_REST_CALL))).thenReturn("-");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_AUTH_TOKEN))).thenReturn("authToken");
    Mockito.when(req.getHeader(Mockito.eq(SSOConstants.X_APP_COMPONENT_ID))).thenReturn("componentId");

    Mockito.doReturn(null).when(authenticator).getUserFromCache(Mockito.eq("authToken"));

    Authentication got = authenticator.validateRequest(req, res, false);
    Assert.assertEquals(Authentication.NOT_CHECKED, got);
  }

}