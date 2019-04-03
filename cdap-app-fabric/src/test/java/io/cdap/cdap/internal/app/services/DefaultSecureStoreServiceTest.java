/*
 * Copyright © 2014-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.internal.app.services;

import io.cdap.cdap.api.security.store.SecureStore;
import io.cdap.cdap.api.security.store.SecureStoreManager;
import io.cdap.cdap.api.security.store.SecureStoreMetadata;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.conf.SConfiguration;
import io.cdap.cdap.common.discovery.EndpointStrategy;
import io.cdap.cdap.common.discovery.RandomEndpointStrategy;
import io.cdap.cdap.common.namespace.NamespaceAdmin;
import io.cdap.cdap.common.test.AppJarHelper;
import io.cdap.cdap.common.utils.Tasks;
import io.cdap.cdap.internal.AppFabricTestHelper;
import io.cdap.cdap.proto.id.EntityId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.proto.id.SecureKeyId;
import io.cdap.cdap.proto.security.Action;
import io.cdap.cdap.proto.security.Authorizable;
import io.cdap.cdap.proto.security.Principal;
import io.cdap.cdap.proto.security.Privilege;
import io.cdap.cdap.security.authorization.AuthorizationUtil;
import io.cdap.cdap.security.authorization.AuthorizerInstantiator;
import io.cdap.cdap.security.authorization.InMemoryAuthorizer;
import io.cdap.cdap.security.spi.authentication.SecurityRequestContext;
import io.cdap.cdap.security.spi.authorization.Authorizer;
import io.cdap.cdap.security.spi.authorization.UnauthorizedException;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Injector;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.apache.twill.filesystem.LocalLocationFactory;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class DefaultSecureStoreServiceTest {
  private static final Principal ALICE = new Principal("alice", Principal.PrincipalType.USER);
  private static final Principal BOB = new Principal("bob", Principal.PrincipalType.USER);
  private static final String KEY1 = "key1";
  private static final String DESCRIPTION1 = "This is the first key";
  private static final String VALUE1 = "caskisgreat";

  private static SecureStore secureStore;
  private static SecureStoreManager secureStoreManager;
  private static AppFabricServer appFabricServer;
  private static Authorizer authorizer;
  private static DiscoveryServiceClient discoveryServiceClient;

  @ClassRule
  public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  @BeforeClass
  public static void setup() throws Exception {
    SConfiguration sConf = SConfiguration.create();
    sConf.set(Constants.Security.Store.FILE_PASSWORD, "secret");
    CConfiguration cConf = createCConf();
    final Injector injector = AppFabricTestHelper.getInjector(cConf, sConf);
    discoveryServiceClient = injector.getInstance(DiscoveryServiceClient.class);
    appFabricServer = injector.getInstance(AppFabricServer.class);
    appFabricServer.startAndWait();
    waitForService(Constants.Service.DATASET_MANAGER);
    secureStore = injector.getInstance(SecureStore.class);
    secureStoreManager = injector.getInstance(SecureStoreManager.class);
    authorizer = injector.getInstance(AuthorizerInstantiator.class).get();

    // Wait for the default namespace creation
    String user = AuthorizationUtil.getEffectiveMasterUser(cConf);
    authorizer.grant(Authorizable.fromEntityId(NamespaceId.DEFAULT), new Principal(user, Principal.PrincipalType.USER),
                     Collections.singleton(Action.ADMIN));
    // Starting the Appfabric server will create the default namespace
    Tasks.waitFor(true, new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        return injector.getInstance(NamespaceAdmin.class).exists(NamespaceId.DEFAULT);
      }
    }, 5, TimeUnit.SECONDS);
    authorizer.revoke(Authorizable.fromEntityId(NamespaceId.DEFAULT), new Principal(user, Principal.PrincipalType.USER),
                      Collections.singleton(Action.ADMIN));
  }

  private static void waitForService(String service) {
    EndpointStrategy endpointStrategy = new RandomEndpointStrategy(() -> discoveryServiceClient.discover(service));
    Preconditions.checkNotNull(endpointStrategy.pick(5, TimeUnit.SECONDS),
                               "%s service is not up after 5 seconds", service);
  }

  @AfterClass
  public static void cleanup() {
    appFabricServer.stopAndWait();
    AppFabricTestHelper.shutdown();
  }

  private static CConfiguration createCConf() throws Exception {
    CConfiguration cConf = CConfiguration.create();
    cConf.set(Constants.CFG_LOCAL_DATA_DIR, TEMPORARY_FOLDER.newFolder().getAbsolutePath());
    cConf.setBoolean(Constants.Security.ENABLED, true);
    cConf.setBoolean(Constants.Security.Authorization.ENABLED, true);
    // we only want to test authorization, but we don't specify principal/keytab, so disable kerberos
    cConf.setBoolean(Constants.Security.KERBEROS_ENABLED, false);
    cConf.setInt(Constants.Security.Authorization.CACHE_MAX_ENTRIES, 0);

    LocationFactory locationFactory = new LocalLocationFactory(TEMPORARY_FOLDER.newFolder());
    Location authorizerJar = AppJarHelper.createDeploymentJar(locationFactory, InMemoryAuthorizer.class);
    cConf.set(Constants.Security.Authorization.EXTENSION_JAR_PATH, authorizerJar.toURI().getPath());

    // set secure store provider
    cConf.set(Constants.Security.Store.PROVIDER, "file");
    return cConf;
  }

  @Test
  public void testSecureStoreAccess() throws Exception {
    final SecureKeyId secureKeyId1 = NamespaceId.DEFAULT.secureKey(KEY1);
    SecurityRequestContext.setUserId(ALICE.getName());
    try {
      secureStoreManager.put(NamespaceId.DEFAULT.getNamespace(), KEY1, VALUE1, DESCRIPTION1,
                             Collections.<String, String>emptyMap());
      Assert.fail("Alice should not be able to store a key since she does not have WRITE privileges on the namespace");
    } catch (UnauthorizedException expected) {
      // expected
    }

    // Grant ALICE admin access to the secure key
    grantAndAssertSuccess(secureKeyId1, ALICE, EnumSet.of(Action.ADMIN));
    // Write should succeed
    secureStoreManager.put(NamespaceId.DEFAULT.getNamespace(), KEY1, VALUE1, DESCRIPTION1,
                           Collections.<String, String>emptyMap());
    // Listing should return the value just written
    List<SecureStoreMetadata> metadatas = secureStore.list(NamespaceId.DEFAULT.getNamespace());
    Assert.assertEquals(1, metadatas.size());
    Assert.assertEquals(KEY1, metadatas.get(0).getName());
    Assert.assertEquals(DESCRIPTION1, metadatas.get(0).getDescription());
    revokeAndAssertSuccess(secureKeyId1, ALICE, EnumSet.allOf(Action.class));

    // Should not be able to list the keys since ALICE does not have privilege on the secure key
    try {
      secureStore.list(NamespaceId.DEFAULT.getNamespace());
    } catch (UnauthorizedException e) {
      // expected
    }

    // Give BOB read access and verify that he can read the stored data
    SecurityRequestContext.setUserId(BOB.getName());
    grantAndAssertSuccess(NamespaceId.DEFAULT, BOB, EnumSet.of(Action.READ));
    grantAndAssertSuccess(secureKeyId1, BOB, EnumSet.of(Action.READ));
    Assert.assertEquals(VALUE1, new String(secureStore.get(NamespaceId.DEFAULT.getNamespace(), KEY1).get(),
                                           Charsets.UTF_8));
    metadatas = secureStore.list(NamespaceId.DEFAULT.getNamespace());
    Assert.assertEquals(1, metadatas.size());

    // BOB should not be able to delete the key
    try {
      secureStoreManager.delete(NamespaceId.DEFAULT.getNamespace(), KEY1);
      Assert.fail("Bob should not be able to delete a key since he does not have ADMIN privileges on the key");
    } catch (UnauthorizedException expected) {
      // expected
    }

    // Grant Bob ADMIN access and he should be able to delete the key
    grantAndAssertSuccess(secureKeyId1, BOB, ImmutableSet.of(Action.ADMIN));
    secureStoreManager.delete(NamespaceId.DEFAULT.getNamespace(), KEY1);
    Assert.assertEquals(0, secureStore.list(NamespaceId.DEFAULT.getNamespace()).size());
    Predicate<Privilege> secureKeyIdFilter = new Predicate<Privilege>() {
      @Override
      public boolean apply(Privilege input) {
        return input.getAuthorizable().equals(Authorizable.fromEntityId(secureKeyId1));
      }
    };
  }

  private void grantAndAssertSuccess(EntityId entityId, Principal principal, Set<Action> actions) throws Exception {
    Set<Privilege> existingPrivileges = authorizer.listPrivileges(principal);
    authorizer.grant(Authorizable.fromEntityId(entityId), principal, actions);
    ImmutableSet.Builder<Privilege> expectedPrivilegesAfterGrant = ImmutableSet.builder();
    for (Action action : actions) {
      expectedPrivilegesAfterGrant.add(new Privilege(entityId, action));
    }
    Assert.assertEquals(Sets.union(existingPrivileges, expectedPrivilegesAfterGrant.build()),
                        authorizer.listPrivileges(principal));
  }

  private void revokeAndAssertSuccess(EntityId entityId, Principal principal, Set<Action> actions) throws Exception {
    Set<Privilege> existingPrivileges = authorizer.listPrivileges(principal);
    authorizer.revoke(Authorizable.fromEntityId(entityId), principal, actions);
    Set<Privilege> revokedPrivileges = new HashSet<>();
    for (Action action : actions) {
      revokedPrivileges.add(new Privilege(entityId, action));
    }
    Assert.assertEquals(Sets.difference(existingPrivileges, revokedPrivileges), authorizer.listPrivileges(principal));
  }
}