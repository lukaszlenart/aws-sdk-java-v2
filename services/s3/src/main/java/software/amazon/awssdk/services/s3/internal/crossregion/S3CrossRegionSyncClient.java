/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.services.s3.internal.crossregion;

import static software.amazon.awssdk.services.s3.internal.crossregion.utils.CrossRegionUtils.REDIRECT_STATUS_CODE;
import static software.amazon.awssdk.services.s3.internal.crossregion.utils.CrossRegionUtils.getBucketRegionFromException;
import static software.amazon.awssdk.services.s3.internal.crossregion.utils.CrossRegionUtils.requestWithDecoratedEndpointProvider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.DelegatingS3Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Request;

/**
 * Decorator S3 Sync client that will fetch the region name whenever there is Redirect 301 error due to cross region bucket
 * access.
 */
@SdkInternalApi
public final class S3CrossRegionSyncClient extends DelegatingS3Client {

    private final Map<String, Region> bucketToRegionCache = new ConcurrentHashMap<>();

    public S3CrossRegionSyncClient(S3Client s3Client) {
        super(s3Client);
    }

    private static <T extends S3Request> Optional<String> bucketNameFromRequest(T request) {
        return request.getValueForField("Bucket", String.class);
    }

    @Override
    protected <T extends S3Request, ReturnT> ReturnT invokeOperation(T request, Function<T, ReturnT> operation) {

        Optional<String> bucketRequest = bucketNameFromRequest(request);
        if (!bucketRequest.isPresent()) {
            return operation.apply(request);
        }
        String bucketName = bucketRequest.get();
        try {
            if (bucketToRegionCache.containsKey(bucketName)) {
                return operation.apply(
                    requestWithDecoratedEndpointProvider(request,
                                                         () -> bucketToRegionCache.get(bucketName),
                                                         serviceClientConfiguration().endpointProvider().get()));
            }
            return operation.apply(request);
        } catch (S3Exception exception) {
            if (exception.statusCode() == REDIRECT_STATUS_CODE) {
                updateCacheFromRedirectException(exception, bucketName);
                return operation.apply(
                    requestWithDecoratedEndpointProvider(request, regionSupplier(bucketName),
                                                         serviceClientConfiguration().endpointProvider().get()));
            }
            throw exception;
        }
    }

    private void updateCacheFromRedirectException(S3Exception exception, String bucketName) {
        Optional<String> regionStr = getBucketRegionFromException(exception);
        // If redirected, clear previous values due to region change.
        bucketToRegionCache.remove(bucketName);
        regionStr.ifPresent(region -> bucketToRegionCache.put(bucketName, Region.of(region)));
    }

    private Supplier<Region> regionSupplier(String bucket) {
        return () -> bucketToRegionCache.computeIfAbsent(bucket, this::fetchBucketRegion);
    }

    private Region fetchBucketRegion(String bucketName) {
        try {
            ((S3Client) delegate()).headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == REDIRECT_STATUS_CODE) {
                return Region.of(getBucketRegionFromException(exception).orElseThrow(() -> exception));
            }
            throw exception;
        }
        return null;
    }


}
