# Codelab: Integrating Google Play Billing for One-Time Products

## 1. Introduction: What this codelab teaches

Welcome to the codelab on integrating the Google Play Billing Library for one-time products in an Android app. This codelab will guide you through the fundamental steps required to sell digital goods, covering both non-consumable (premium features) and consumable (in-game currency) items.

You will learn how to:
*   Add the Play Billing Library to your app.
*   Connect to Google Play's billing service.
*   Query for available products you've configured in the Google Play Console.
*   Initiate the purchase flow for users.
*   Process and verify purchases.
*   Distinguish between and handle acknowledging non-consumable purchases and consuming consumable ones.
*   Understand best practices for handling various billing responses.

By the end of this codelab, you will have a clear understanding of the core implementation details for selling one-time products with the Play Billing Library.

## 2. What are One-Time Products?

A one-time product is a type of digital content that a user can purchase with a single, non-recurring payment. Google Play Billing supports two types of one-time products:

*   **Non-consumable:** A product that is purchased once to provide a permanent benefit. Examples include unlocking a premium feature, removing ads, or granting access to a game level. These purchases are permanently associated with the user's Google account and can be restored on new devices.
*   **Consumable:** A product that can be purchased multiple times and is "consumed" to provide a temporary benefit. Examples include in-game currency, extra lives, or a temporary power-up. Once consumed, the product can be purchased again.

For a more detailed explanation, refer to the official documentation on [One-time products](https://developer.android.com/google/play/billing/one-time-products).

## 3. Setup Play Billing Library and Connect to Google Play

The first step is to add the Play Billing Library dependency to your project and establish a connection to the Google Play service.

### Add the Gradle Dependency

In your app-level `build.gradle` file, add the dependency for the Play Billing Library.

**`app/build.gradle`**
```groovy
dependencies {
    // ... other dependencies
    implementation "com.android.billingclient:billing:8.1.0"
    // ... other dependencies
}
```

### Create and Configure the BillingClient

All interactions with the Play Billing Library are managed through a `BillingClient` instance. It's best practice to encapsulate this logic in a dedicated class. In this sample, we use `BillingServiceClient`.

The `BillingClient` is initialized using a builder, where you set a `PurchasesUpdatedListener` to handle purchase results and enable support for pending purchases.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
  protected BillingClient createBillingClient() {
    return BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build();
  }
```

### Connect to Google Play

Once the `BillingClient` is created, you must establish a connection to Google Play. This is an asynchronous operation. You start the connection by calling `startConnection` and passing a `BillingClientStateListener` to handle the result.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
  public void startBillingConnection(ImmutableList<Product> productList) {
    Log.i(TAG, "Product list sent: " + productList);
    Log.i(TAG, "Starting connection");
    billingClient.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingResponseCode.OK) {
              // The BillingClient is ready. You can query products here.
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
```

### Check your work

After adding this code, your application will attempt to connect to the Google Play billing service as soon as the `BillingServiceClient` is initialized. You can look for the "Billing connection failed" or a success log message in your Logcat to verify the connection attempt.

## 4. Query for Available Products

Once connected to Google Play, you can query for the products you have defined in the Google Play Console. This is done by providing a list of product IDs.

### Query Logic

The `queryProductDetailsAsync` method takes a `QueryProductDetailsParams` object, which contains the list of `Product` objects (with their IDs and types) you want to query. The result is returned asynchronously to a `ProductDetailsResponseListener`.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
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
            
            // process returned productDetailsList and notify the UI
            setupProductDetailsMap(productDetailsResponse.getProductDetailsList());
            billingServiceClientListener.onProductDetailsFetched(productDetailsMap);
          }
        });
  }
```

### Receiving the Results

The `onProductDetailsResponse` callback provides a list of `ProductDetails` objects. Each object contains important information about a product, such as its name, description, and price. In our sample, the `MainActivity` implements a listener to receive these details and update the UI.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/MainActivity.java`**
```java
  @Override
  public void onProductDetailsFetched(Map<String, ProductDetails> productDetailsMap) {
      runOnUiThread(
              () -> {
                  for (ProductDetails productDetails : productDetailsMap.values()) {
                      // In a real app, you would update your UI here
                      // updateProductCardUI(productDetails);
                      Log.i("MainActivity", "Fetched product: " + productDetails.getName());
                  }
              });
  }
```

### Check your work

After implementing this step, run your app and check Logcat. You should see logs indicating the billing result after querying and the names of the products that were successfully fetched from the Play Console. If the UI were connected, you would see product details populated on the screen.

## 5. Initiate the Purchase Flow

Now that you can display your products, the next step is to allow users to purchase them. This is done by launching the Google Play purchase screen.

The `launchBillingFlow` method is called with the `Activity` and a `BillingFlowParams` object. The `BillingFlowParams` must contain the `ProductDetails` object for the item the user wishes to buy.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
  public void launchBillingFlow(String productId) {
    ProductDetails productDetails = productDetailsMap.get(productId);
    if (productDetails == null) {
      Log.e(
          TAG, "Cannot launch billing flow: ProductDetails not found for productId: " + productId);
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
```

This method is typically called from a UI element, like a "buy" button's click listener.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/MainActivity.java`**
```java
        // Inside a method that configures your UI, like updateProductCardUI...
        // buyButton.setOnClickListener(v -> billingServiceClient.launchBillingFlow(productId));
```

### Check your work

Run the app and trigger the `launchBillingFlow` method (e.g., by clicking a button). The Google Play purchase sheet should appear from the bottom of the screen, displaying the product details and asking the user to confirm their purchase.

## 6. Handle Purchase Results

When the user completes or cancels the purchase flow, your app receives the result in your `PurchasesUpdatedListener`.

Your listener's `onPurchasesUpdated` method is the single entry point for all purchase results. It's critical to check the `BillingResult` and the list of `Purchase` objects to correctly process the outcome. If the response code is `OK`, you should process the successful purchases by calling a handler method.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
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
```

### Check your work

At this stage, you can test the purchase flow. When you complete a test purchase, check Logcat for the message "Purchase failed" or the `OK` response code along with delegation to `handlePurchase`. If you cancel, you should see the "User cancelled" log message.

## 7. Acknowledge a Non-Consumable Purchase

For non-consumable products, you must **acknowledge** the purchase within three days. Acknowledging a purchase confirms to Google Play that you have granted the user entitlement to the item. If you fail to do so, Google will automatically refund the purchase and revoke it.

The logic for handling a purchase should check its state and then, if it's a non-consumable, call `acknowledgePurchase`.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
  private void handlePurchase(Purchase purchase) {
    // Note: It's recommended to verify the purchase on a secure backend server.
    
    if (purchase.getPurchaseState() == PurchaseState.PURCHASED && !purchase.isAcknowledged()) {

      if (shouldConsume(purchase)) {
        // Logic for consumable products (see next step)
      } else {
        // This is a non-consumable product. Acknowledge it.
        AcknowledgePurchaseParams acknowledgePurchaseParams =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(
            acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
      }
    }
  }
```

The `acknowledgePurchaseResponseListener` simply logs the result of the acknowledgment call.

### Check your work

Complete a test purchase for a non-consumable product. Check Logcat for the "Acknowledge purchase response" message with a response code of `OK`. If you try to purchase the same item again, Google Play should show a dialog indicating you already own it.

## 8. Consume a Consumable Purchase

For consumable products, the process is different. Instead of acknowledging, you must **consume** the purchase. Consuming a purchase fulfills the one-time order and allows the user to buy the same product again.

We first need a way to differentiate between consumable and non-consumable products. In this sample, we use a simple prefix-based check on the product ID.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
  private boolean shouldConsume(Purchase purchase) {
    return purchase.getProducts().stream()
        .allMatch(productId -> productId.startsWith(CONSUMABLE_PRODUCT_PREFIX));
  }
```

Then, in our `handlePurchase` method, we call `consumeAsync` for products that `shouldConsume` returns true for.

**`app/src/main/java/com/google/play/billing/samples/onetimepurchases/billing/BillingServiceClient.java`**
```java
  private void handlePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() == PurchaseState.PURCHASED && !purchase.isAcknowledged()) {

      if (shouldConsume(purchase)) {
        // This is a consumable product. Consume it.
        ConsumeParams consumeParams =
            ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
        billingClient.consumeAsync(consumeParams, consumeResponseListener);

      } else {
        // Logic for non-consumable products
      }
    }
  }
```
The `consumeResponseListener` logs the result of the consumption call.

### Check your work

Complete a test purchase for a product you've defined as consumable (e.g., `consumable_product_01`). Check Logcat for the "Consume response" message. After the purchase is successfully consumed, you should be able to purchase the exact same item again.

## 9. Best Practices for BillingResult Response Codes

Properly handling the `BillingResult` response codes is essential for creating a robust and user-friendly billing implementation. The code you receive in `onPurchasesUpdated` and other callbacks tells you exactly what happened.

Here are some of the most common response codes and how to handle them:

*   `BillingResponseCode.OK`: The operation was successful (e.g., purchase completed, query successful).
*   `BillingResponseCode.USER_CANCELED`: The user canceled the flow (e.g., closed the purchase sheet). This is a normal and expected outcome; you should typically do nothing or dismiss any loading indicators.
*   `BillingResponseCode.ITEM_ALREADY_OWNED`: The user is trying to buy a non-consumable product they already own. Your app should gracefully handle this, inform the user they already have the item, and ensure they have been granted the entitlement.
*   `BillingResponseCode.SERVICE_DISCONNECTED`: The connection to the Google Play service was lost. You should implement a retry policy to re-establish the connection. The `BillingClient`'s `enableAutoServiceReconnection()` helps with this for transient errors.
*   `BillingResponseCode.DEVELOPER_ERROR`: There's an issue with your implementation, such as calling an API incorrectly (e.t., not calling `startConnection`). You should not release an app with logic that can cause this error.

For a complete list of codes and recommended actions, always refer to the official documentation on [Handling billing errors](https://developer.android.com/google/play/billing/errors).
