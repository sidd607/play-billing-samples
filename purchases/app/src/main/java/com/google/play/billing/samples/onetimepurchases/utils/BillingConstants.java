/* Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.play.billing.samples.onetimepurchases.utils;

import com.android.billingclient.api.BillingClient.ProductType;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.google.common.collect.ImmutableList;

public final class BillingConstants {
    private BillingConstants() {}

    public static final String SUBS_PRODUCT_01 = "sidd607_subs";
    public static final String SUBS_PRODUCT_02 = "sidd607_subs_premium";
    public static final String ADDON_SUBS_PRODUCT_01 = "kids_package";
    public static final String ADDON_SUBS_PRODUCT_02 = "news_package";
    public static final String ADDON_SUBS_PRODUCT_03 = "sports_package";

    public static final ImmutableList<Product> PRODUCT_LIST =
        ImmutableList.of(
            Product.newBuilder().setProductId(SUBS_PRODUCT_01).setProductType(ProductType.SUBS).build(),
            Product.newBuilder().setProductId(SUBS_PRODUCT_02).setProductType(ProductType.SUBS).build(),
            Product.newBuilder().setProductId(ADDON_SUBS_PRODUCT_01).setProductType(ProductType.SUBS).build(),
            Product.newBuilder().setProductId(ADDON_SUBS_PRODUCT_02).setProductType(ProductType.SUBS).build(),
            Product.newBuilder().setProductId(ADDON_SUBS_PRODUCT_03).setProductType(ProductType.SUBS).build());
}