/*
 * Copyright (c) Microsoft Corporation
 *
 * All rights reserved.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.microsoft.intellij.runner.container.utils;

import com.intellij.openapi.project.Project;
import com.microsoft.azuretools.azurecommons.util.Utils;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.RegistryAuth;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class DockerUtil {
    /**
     * create a docker file in specified folder.
     */
    public static void createDockerFile(String basePath, String folderName, String filename, String content)
            throws IOException {
        if (Utils.isEmptyString(basePath)) {
            throw new FileNotFoundException("Project basePath is null.");
        }
        //noinspection ResultOfMethodCallIgnored
        Paths.get(basePath, folderName).toFile().mkdirs();
        Path dockerFilePath = Paths.get(basePath, folderName, filename);
        if (!dockerFilePath.toFile().exists()) {
            byte[] bytes = content.getBytes();
            Files.write(dockerFilePath, bytes);
        }
    }

    /**
     * create container with specified ImageName:TagName.
     */
    public static String createContainer(DockerClient docker, String imageNameWithTag)
            throws DockerException, InterruptedException {
        final Map<String, List<PortBinding>> portBindings = new HashMap<>();
        List<PortBinding> randomPort = new ArrayList<>();
        PortBinding randomBinding = PortBinding.randomPort("0.0.0.0");
        randomPort.add(randomBinding);
        portBindings.put(Constant.TOMCAT_SERVICE_PORT, randomPort);

        final HostConfig hostConfig = HostConfig.builder().portBindings(portBindings).build();

        final ContainerConfig config = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .image(imageNameWithTag)
                .exposedPorts(Constant.TOMCAT_SERVICE_PORT)
                .build();
        final ContainerCreation container = docker.createContainer(config);
        return container.id();
    }

    /**
     * runContainer.
     */
    public static Container runContainer(DockerClient docker, String containerId) throws DockerException,
            InterruptedException {
        docker.startContainer(containerId);
        List<Container> containers = docker.listContainers();
        Optional<Container> container = containers.stream().filter(item -> item.id().equals(containerId))
                .findFirst();
        if (container.isPresent()) {
            return container.get();
        } else {
            throw new DockerException("Error in starting container.");
        }
    }

    /**
     * build image.
     */
    public static String buildImage(DockerClient docker, String imageNameWithTag, Path dockerDirectory,
                                    String dockerFile, ProgressHandler progressHandler)
            throws DockerException, InterruptedException, IOException {
        String imageId = docker.build(dockerDirectory, imageNameWithTag, dockerFile, progressHandler);
        return imageId == null ? null : imageNameWithTag;
    }

    /**
     * Push image to a private registry.
     */
    public static void pushImage(DockerClient dockerClient, String registryUrl, String registryUsername,
                                 String registryPassword, String targetImageName,
                                 ProgressHandler handler)
            throws DockerException, InterruptedException {
        final RegistryAuth registryAuth = RegistryAuth.builder().username(registryUsername).password(registryPassword)
                .build();
        if (targetImageName.startsWith(registryUrl)) {
            dockerClient.push(targetImageName, handler, registryAuth);
        } else {
            throw new DockerException("serverUrl and imageName mismatch.");
        }
    }

    /**
     * Pull image from a private registry.
     */
    public static void pullImage(DockerClient dockerClient, String registryUrl, String registryUsername,
                                 String registryPassword, String targetImageName)
            throws DockerException, InterruptedException {
        final RegistryAuth registryAuth = RegistryAuth.builder().username(registryUsername).password(registryPassword)
                .build();
        if (targetImageName.startsWith(registryUrl)) {
            dockerClient.pull(targetImageName, registryAuth);
        } else {
            throw new DockerException("serverUrl and imageName mismatch.");
        }

    }

    /**
     * Stop a container by id.
     */
    public static void stopContainer(DockerClient dockerClient, String runningContainerId) throws DockerException,
            InterruptedException {
        if (runningContainerId != null) {
            dockerClient.stopContainer(runningContainerId, Constant.TIMEOUT_STOP_CONTAINER);
            dockerClient.removeContainer(runningContainerId);
        }
    }

    /**
     * Get DockerClient instance.
     */
    public static DockerClient getDockerClient(String dockerHost, boolean tlsEnabled, String certPath) throws
            DockerCertificateException {
        if (tlsEnabled) {
            return DefaultDockerClient.builder().uri(URI.create(dockerHost))
                    .dockerCertificates(new DockerCertificates(Paths.get(certPath)))
                    .build();
        } else {
            return DefaultDockerClient.builder().uri(URI.create(dockerHost)).build();
        }
    }

    /**
     * check if the default docker file exists (in project base path).
     * If yes, return the path as a String.
     * Else return an empty String.
     */
    public static String getDefaultDockerFilePathIfExist(Project project) {
        String basePath = project.getBasePath();
        if (!Utils.isEmptyString(basePath)) {
            Path targetDockerfile = Paths.get(basePath, Constant.DOCKERFILE_NAME);
            if (targetDockerfile != null && targetDockerfile.toFile().exists()) {
                return targetDockerfile.toString();
            }
        }
        return "";
    }
}
