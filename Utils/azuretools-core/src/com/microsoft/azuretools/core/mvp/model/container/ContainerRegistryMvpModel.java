/*
 * Copyright (c) Microsoft Corporation
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.microsoft.azuretools.core.mvp.model.container;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.containerregistry.Registries;
import com.microsoft.azure.management.containerregistry.Registry;
import com.microsoft.azure.management.containerregistry.RegistryPassword;
import com.microsoft.azure.management.containerregistry.implementation.RegistryListCredentials;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azuretools.authmanage.AuthMethodManager;
import com.microsoft.azuretools.azurecommons.helpers.NotNull;
import com.microsoft.azuretools.azurecommons.util.Utils;
import com.microsoft.azuretools.core.mvp.model.AzureMvpModel;
import com.microsoft.azuretools.core.mvp.model.webapp.PrivateRegistryImageSetting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ContainerRegistryMvpModel {

    private final Map<String, List<Registry>> subscriptionIdToRegistryMap;

    private static final String CANNOT_GET_REGISTRY = "Cannot get Registry with resource Id: ";
    private static final String CANNOT_GET_CREDENTIAL = "Cannot get credential.";
    private static final String ADMIN_USER_NOT_ENABLED = "Admin user is not enabled.";

    private ContainerRegistryMvpModel() {
        subscriptionIdToRegistryMap = new ConcurrentHashMap<>();
    }

    private static final class SingletonHolder {
        private static final ContainerRegistryMvpModel INSTANCE = new ContainerRegistryMvpModel();
    }

    public static ContainerRegistryMvpModel getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Get Registry instances mapped by Subscription id.
     */
    public Map<String, List<Registry>> getContainerRegistryMap(boolean force) throws IOException {
        List<Subscription> subscriptions = AzureMvpModel.getInstance().getSelectedSubscriptions();
        for (Subscription sub : subscriptions) {
            listRegistryBySubscriptionId(sub.subscriptionId(), force);
        }
        return subscriptionIdToRegistryMap;
    }

    /**
     * Get Registry by subscription id.
     */
    public List<Registry> listRegistryBySubscriptionId(@NotNull String sid, boolean force) {
        if (!force && subscriptionIdToRegistryMap.containsKey(sid)) {
            return subscriptionIdToRegistryMap.get(sid);
        }
        List<Registry> registryList = new ArrayList<>();
        try {
            Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
            registryList.addAll(azure.containerRegistries().list());
            subscriptionIdToRegistryMap.put(sid, registryList);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return registryList;
    }

    /**
     * Get Registry Credential.
     */
    public PrivateRegistryImageSetting createImageSettingWithRegistry(@NotNull final Registry registry) throws Exception {
        if (!registry.adminUserEnabled()) {
            throw new Exception(ADMIN_USER_NOT_ENABLED);
        }
        final RegistryListCredentials credentials = registry.listCredentials();
        if (credentials == null) {
            throw new Exception(CANNOT_GET_CREDENTIAL);
        }
        String username = credentials.username();
        final List<RegistryPassword> passwords = credentials.passwords();
        if (Utils.isEmptyString(username) || passwords == null || passwords.size() == 0) {
            throw new Exception(CANNOT_GET_CREDENTIAL);
        }
        return new PrivateRegistryImageSetting(registry.loginServerUrl(), username, passwords.get(0).value(), null,
                null);
    }

    /**
     * Get ACR by Id.
     */
    @NotNull
    public Registry getContainerRegistry(String sid, String id) throws Exception {
        Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
        Registries registries = azure.containerRegistries();
        if (registries == null) {
            throw new Exception(CANNOT_GET_REGISTRY + id);
        }
        Registry registry = registries.getById(id);
        if (registry == null) {
            throw new Exception(CANNOT_GET_REGISTRY + id);
        }
        return registry;
    }

    /**
     * Set AdminUser enabled status of container registry.
     */
    public Registry setAdminUserEnabled(String sid, String id, boolean enabled) throws IOException {
        Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
        Registries registries = azure.containerRegistries();
        if (registries != null) {
            Registry registry = registries.getById(id);
            if (registry != null) {
                if (enabled) {
                    registry.update().withRegistryNameAsAdminUser().apply();
                } else {
                    registry.update().withoutRegistryNameAsAdminUser().apply();
                }
            }
            return registry;
        } else {
            return null;
        }
    }

    private void clearRegistryMap() {
        subscriptionIdToRegistryMap.clear();
    }
}
