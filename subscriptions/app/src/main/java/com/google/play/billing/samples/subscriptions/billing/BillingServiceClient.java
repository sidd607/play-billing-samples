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
package com.google.play.billing.samples.subscriptions.billing;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams;
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchaseState;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manages interactions with the Google Play Billing Library for handling subscriptions. */
public class BillingServiceClient {

  private static final String TAG = "Billing Service Client";
  private final BillingClient billingClient;
  private final AppCompatActivity activity;
  private BillingServiceClientListener billingServiceClientListener;

  // Map to store product details for the products that are available to the user.
  private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();

  private final AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener =
      billingResult ->
          Log.i(TAG, "Acknowledge purchase response: " + billingResult.getResponseCode());

  private final PurchasesUpdatedListener purchasesUpdatedListener =
      (billingResult, purchases) -> {
        int responseCode = billingResult.getResponseCode();
        billingServiceClientListener.onBillingResponse(responseCode, billingResult);

        if (responseCode == BillingResponseCode.OK && purchases != null) {
          for (Purchase purchase : purchases) {
            handlePurchase(purchase);
          }
          billingServiceClientListener.onPurchasesFetched(purchases);
        } else if (responseCode == BillingResponseCode.USER_CANCELED) {
          Log.e(TAG, "Purchase failed: User cancelled");
        } else {
          Log.e(TAG, "Purchase failed: " + responseCode);
        }
      };

  public BillingServiceClient(
      AppCompatActivity activity,
      @NonNull BillingServiceClientListener billingServiceClientListener) {
    this.activity = activity;
    this.billingServiceClientListener = billingServiceClientListener;
    billingClient = createBillingClient();
  }

  protected BillingClient createBillingClient() {
    return BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build();
  }

  public void startBillingConnection(ImmutableList<Product> productList) {
    if (billingClient.getConnectionState() != BillingClient.ConnectionState.DISCONNECTED) {
      Log.w(
          TAG,
          "startBillingConnection: BillingClient is already connecting or connected. State: "
              + billingClient.getConnectionState());
      return;
    }

    Log.i(TAG, "Starting connection");
    billingClient.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingResponseCode.OK) {
              queryProductDetails(productList);
            } else {
              Log.e(TAG, "Billing connection failed: " + billingResult.getDebugMessage());
            }
          }

          @Override
          public void onBillingServiceDisconnected() {
            Log.e(TAG, "Billing Service connection lost.");
          }
        });
  }

  /** Basic launchBillingFlow for new purchases. */
  public void launchBillingFlow(String productId, String offerToken) {
    ProductDetails productDetails = productDetailsMap.get(productId);
    if (productDetails == null) {
      Log.e(TAG, "ProductDetails not found for: " + productId);
      return;
    }

    ProductDetailsParams productDetailsParams =
        ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build();

    List<ProductDetailsParams> productDetailsParamsList = ImmutableList.of(productDetailsParams);

    BillingFlowParams billingFlowParams =
        BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build();

    billingClient.launchBillingFlow(activity, billingFlowParams);
  }

  /** Comprehensive launchBillingFlow for all cases. */
  public void launchBillingFlow(
      String productId,
      String offerToken,
      String oldPurchaseToken,
      String oldProductId,
      int replacementMode) {

    ProductDetails productDetails = productDetailsMap.get(productId);
    if (productDetails == null) {
      Log.e(TAG, "ProductDetails not found for: " + productId);
      return;
    }

    SubscriptionProductReplacementParams subscriptionProductReplacementParams =
        SubscriptionProductReplacementParams.newBuilder()
            .setOldProductId(oldProductId)
            .setReplacementMode(replacementMode)
            .build();

    ProductDetailsParams productDetailsParams =
        ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setSubscriptionProductReplacementParams(subscriptionProductReplacementParams)
            .setOfferToken(offerToken)
            .build();

    List<ProductDetailsParams> productDetailsParamsList = ImmutableList.of(productDetailsParams);

    BillingFlowParams billingFlowParams =
        BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .setSubscriptionUpdateParams(
                SubscriptionUpdateParams.newBuilder().setOldPurchaseToken(oldPurchaseToken).build())
            .build();

    billingClient.launchBillingFlow(activity, billingFlowParams);
  }

  public void endBillingConnection() {
    billingClient.endConnection();
  }

  public void queryPurchases() {
    if (billingClient == null || !billingClient.isReady()) {
      Log.w(TAG, "queryPurchases: BillingClient is not ready or null");
      return;
    }
    QueryPurchasesParams queryPurchasesParams =
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build();
    billingClient.queryPurchasesAsync(
        queryPurchasesParams,
        (billingResult, purchases) -> {
          if (billingResult.getResponseCode() == BillingResponseCode.OK) {
            billingServiceClientListener.onPurchasesFetched(purchases);
          } else {
            Log.e(TAG, "queryPurchases failed: " + billingResult.getDebugMessage());
          }
        });
  }

  private void handlePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() == PurchaseState.PURCHASED && !purchase.isAcknowledged()) {
      AcknowledgePurchaseParams acknowledgePurchaseParams =
          AcknowledgePurchaseParams.newBuilder()
              .setPurchaseToken(purchase.getPurchaseToken())
              .build();
      billingClient.acknowledgePurchase(
          acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
    }
  }

  public void queryProductDetails(ImmutableList<Product> productList) {
    QueryProductDetailsParams queryProductDetailsParams =
        QueryProductDetailsParams.newBuilder().setProductList(productList).build();

    billingClient.queryProductDetailsAsync(
        queryProductDetailsParams,
        (billingResult, productDetailsResponse) -> {
          if (billingResult.getResponseCode() == BillingResponseCode.OK) {
            List<ProductDetails> detailsList = productDetailsResponse.getProductDetailsList();
            for (ProductDetails details : detailsList) {
              productDetailsMap.put(details.getProductId(), details);
            }
            billingServiceClientListener.onProductDetailsFetched(productDetailsMap);
            queryPurchases();
          }
        });
  }
}
