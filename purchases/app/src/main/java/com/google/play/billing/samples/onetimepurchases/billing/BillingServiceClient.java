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

import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchaseState;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages interactions with the Google Play Billing Library for handling one-time purchases.
 *
 * <p>This class encapsulates the setup of the {@link BillingClient}, manages the connection
 * lifecycle (including retries), queries product details, initiates the purchase flow, and
 * processes purchase updates by acknowledging them and notifying a listener.
 */
public class BillingServiceClient {

  private static final String TAG = "Billing Service Client";
  private static final String CONSUMABLE_PRODUCT_PREFIX = "consumable_";
  private final BillingClient billingClient;
  private final AppCompatActivity activity;
  private final BillingServiceClientListener billingServiceClientListener;
  // Map to store product details for the products that are available to the user.
  private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();
  private final AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener =
      new AcknowledgePurchaseResponseListener() {
        @Override
        public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
          Log.i(TAG, "Acknowledge purchase response: " + billingResult.getResponseCode());
        }
      };

  private final ConsumeResponseListener consumeResponseListener =
      new ConsumeResponseListener() {
        @Override
        public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
          Log.i(TAG, "Consume response: " + billingResult.getResponseCode());
        }
      };

  private final PurchasesUpdatedListener purchasesUpdatedListener =
      new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
          int responseCode = billingResult.getResponseCode();
          billingServiceClientListener.onBillingResponse(responseCode, billingResult);

          if (responseCode == BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
              handlePurchase(purchase);
            }
          } else if (responseCode == BillingResponseCode.USER_CANCELED) {
            Log.e(TAG, "Purchase failed: User cancelled");
          } else {
            Log.e(TAG, "Purchase failed");
          }
        }
      };

  /**
   * @param activity The activity instance from which the billing flow will be launched.
   * @param billingServiceClientListener The listener to receive billing responses.
   */
  public BillingServiceClient(
      AppCompatActivity activity, BillingServiceClientListener billingServiceClientListener) {
    this.activity = activity;
    this.billingServiceClientListener = billingServiceClientListener;
    billingClient = createBillingClient();
  }

  /**
   * Starts the billing connection with Google Play. This method should be called exactly once
   * before any other methods in this class.
   *
   * @param productList The list of products to query for after the connection is established.
   */
  public void startBillingConnection(ImmutableList<Product> productList) {
    Log.i(TAG, "Product list sent: " + productList);
    Log.i(TAG, "Starting connection");
    billingClient.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingResponseCode.OK) {
              // Query product details to get the product details list.
              queryProductDetails(productList);
            } else {
              // BillingClient.enableAutoServiceReconnection() will retry the connection on
              // transient errors automatically.
              // We don't need to retry on terminal errors (e.g., BILLING_UNAVAILABLE,
              // DEVELOPER_ERROR).
              Log.e(TAG, "Billing connection failed: " + billingResult.getDebugMessage());
              Log.e(TAG, "Billing response code: " + billingResult.getResponseCode());
            }
          }

          @Override
          public void onBillingServiceDisconnected() {
            Log.e(TAG, "Billing Service connection lost.");
          }
        });
  }

  /**
   * Launches the billing flow for the product with the given product ID.
   *
   * @param productId The product ID of the product to purchase.
   */
  public void launchBillingFlow(String productId) {
    ProductDetails productDetails = productDetailsMap.get(productId);
    if (productDetails == null) {
      Log.e(
          TAG, "Cannot launch billing flow: ProductDetails not found for productId: " + productId);
      billingServiceClientListener.onBillingResponse(
          BillingResponseCode.ITEM_UNAVAILABLE,
          BillingResult.newBuilder().setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE).build());
      return;
    }
    ImmutableList<ProductDetailsParams> productDetailsParamsList =
        ImmutableList.of(
            ProductDetailsParams.newBuilder().setProductDetails(productDetails).build());

    BillingFlowParams billingFlowParams =
        BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build();

    billingClient.launchBillingFlow(activity, billingFlowParams);
  }

  /**
   * Ends the billing connection with Google Play. This method should be called when the app is
   * closed.
   */
  public void endBillingConnection() {
    billingClient.endConnection();
  }

  /**
   * @param productDetailsList The list of {@link ProductDetails} to populate the map.
   */
  protected void setupProductDetailsMap(List<ProductDetails> productDetailsList) {
    productDetailsList.forEach(
        productDetails -> productDetailsMap.put(productDetails.getProductId(), productDetails));
    Log.i(TAG, "Rendered products map: " + productDetailsMap);
  }

  protected BillingClient createBillingClient() {
    return BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build();
  }

  private void handlePurchase(Purchase purchase) {
    // Step 1: Send the purchase to your secure backend to verify the purchase following
    // https://developer.android.com/google/play/billing/security#verify

    // Step 2: Update your entitlement storage with the purchase. If purchase is
    // in PENDING state then ensure the entitlement is marked as pending and the
    // user does not receive benefits yet. It is recommended that this step is
    // done on your secure backend and can combine in the API call to your
    // backend in step 1.

    // Step 3: Notify the user using appropriate messaging.
    if (purchase.getPurchaseState() == PurchaseState.PURCHASED) {
      for (String product : purchase.getProducts()) {
        Log.d(TAG, product + " purchased successfully! ");
      }
    }

    // Step 4: Notify Google the purchase was processed.
    // For one-time products, acknowledge the purchase.
    // This sample app (client-only) uses billingClient.acknowledgePurchase().
    // For consumable one-time products, consume the purchase
    // This sample app (client-only) uses billingClient.consumeAsync()
    // If you have a secure backend, you must acknowledge purchases on your server using the
    // server-side API.
    // See https://developer.android.com/google/play/billing/security#acknowledge
    if (purchase.getPurchaseState() == PurchaseState.PURCHASED && !purchase.isAcknowledged()) {

      if (shouldConsume(purchase)) {
        ConsumeParams consumeParams =
            ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
        billingClient.consumeAsync(consumeParams, consumeResponseListener);

      } else {
        AcknowledgePurchaseParams acknowledgePurchaseParams =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(
            acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
      }
    }
  }

  private void queryProductDetails(ImmutableList<Product> productList) {
    Log.i(TAG, "Querying products for: " + productList);
    QueryProductDetailsParams queryProductDetailsParams =
        QueryProductDetailsParams.newBuilder().setProductList(productList).build();
    billingClient.queryProductDetailsAsync(
        queryProductDetailsParams,
        new ProductDetailsResponseListener() {
          @Override
          public void onProductDetailsResponse(
              BillingResult billingResult, QueryProductDetailsResult productDetailsResponse) {
            // check billingResult
            Log.i(TAG, "Billing result after querying: " + billingResult.getResponseCode());
            // process returned productDetailsList
            Log.i(
                TAG,
                "Print unfetched products: " + productDetailsResponse.getUnfetchedProductList());
            setupProductDetailsMap(productDetailsResponse.getProductDetailsList());
            billingServiceClientListener.onProductDetailsFetched(productDetailsMap);
          }
        });
  }

  /**
   * Determines if a purchase should be consumed. **Note:** This implementation is provided as an
   * example. Developers should implement their specific business logic to accurately determine if a
   * purchase should be consumed.
   *
   * @param purchase The purchase to check.
   * @return True if the purchase should be consumed, false otherwise.
   */
  private boolean shouldConsume(Purchase purchase) {
    return purchase.getProducts().stream()
        .allMatch(productId -> productId.startsWith(CONSUMABLE_PRODUCT_PREFIX));
  }
}
