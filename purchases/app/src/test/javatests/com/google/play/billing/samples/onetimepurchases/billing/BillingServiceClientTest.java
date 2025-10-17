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
package com.google.play.billing.samples.onetimepurchases.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.support.v7.app.AppCompatActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClient.ProductType;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.google.common.collect.ImmutableList;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;

/** Unit tests for {@link BillingServiceClient} */
@RunWith(AndroidJUnit4.class)
public class BillingServiceClientTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private BillingServiceClient billingServiceClient;
  private AppCompatActivity defaultActivity;

  private ProductDetails productDetails;

  @Mock private BillingClient mockBillingClient;
  @Mock private BillingServiceClientListener mockBillingServiceClientListener;

  private static final String TEST_PRODUCT_ID = "test_product_id";
  private static final String TEST_WRONG_PRODUCT_ID = "wrong_product_id";
  private static final String PRODUCT_DETAILS_JSON =
      "{\"productId\":\"test_product_id\",\"type\":\"inapp\",\"title\":\"Test Product Title\"}";
  private static final Product TEST_PRODUCT =
      Product.newBuilder().setProductId(TEST_PRODUCT_ID).setProductType(ProductType.INAPP).build();

  private class TestableBillingServiceClient extends BillingServiceClient {
    private TestableBillingServiceClient(
        AppCompatActivity activity, BillingServiceClientListener billingServiceClientListener) {
      super(activity, billingServiceClientListener);
    }

    @Override
    protected BillingClient createBillingClient() {
      return mockBillingClient;
    }
  }

  @Before
  public void setUp() {
    defaultActivity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
    billingServiceClient =
        new TestableBillingServiceClient(defaultActivity, mockBillingServiceClientListener);
    try {
      productDetails = ProductDetails.fromJson(PRODUCT_DETAILS_JSON);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
    billingServiceClient.setupProductDetailsMap(ImmutableList.of(productDetails));
  }

  @Test
  public void testStartBillingConnection() {
    billingServiceClient.startBillingConnection(ImmutableList.of(TEST_PRODUCT));
    verify(mockBillingClient).startConnection(any());
  }

  @Test
  public void testLaunchBillingFlow_success() {
    billingServiceClient.launchBillingFlow(TEST_PRODUCT_ID);
    verify(mockBillingClient).launchBillingFlow(any(), any());
  }

  @Test
  public void testLaunchBillingFlow_productNotFound() {
    billingServiceClient.launchBillingFlow(TEST_WRONG_PRODUCT_ID);
    verify(mockBillingServiceClientListener)
        .onBillingResponse(eq(BillingResponseCode.ITEM_UNAVAILABLE), any(BillingResult.class));
    verify(mockBillingClient, never()).launchBillingFlow(any(), any());
  }

  @Test
  public void testLaunchBillingFlow_launchFailure() {
    BillingResult billingResult =
        BillingResult.newBuilder().setResponseCode(BillingResponseCode.ERROR).build();
    when(mockBillingClient.launchBillingFlow(any(), any())).thenReturn(billingResult);

    billingServiceClient.launchBillingFlow(TEST_PRODUCT_ID);

    verify(mockBillingClient).launchBillingFlow(any(), any());
  }
}