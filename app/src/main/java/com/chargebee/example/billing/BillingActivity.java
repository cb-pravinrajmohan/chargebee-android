package com.chargebee.example.billing;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.chargebee.android.ProgressBarListener;
import com.chargebee.android.billingservice.CBCallback;
import com.chargebee.android.billingservice.CBPurchase;
import com.chargebee.android.billingservice.OneTimeProductType;
import com.chargebee.android.billingservice.ProductType;
import com.chargebee.android.exceptions.CBException;
import com.chargebee.android.models.CBProduct;
import com.chargebee.android.models.NonSubscription;
import com.chargebee.android.network.CBCustomer;
import com.chargebee.example.BaseActivity;
import com.chargebee.example.R;
import com.chargebee.example.adapter.ProductListAdapter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import static com.chargebee.example.util.Constants.PRODUCTS_LIST_KEY;

public class BillingActivity extends BaseActivity implements ProductListAdapter.ProductClickListener, ProgressBarListener {

    private ArrayList<CBProduct> productList = null;
    private ProductListAdapter productListAdapter = null;
    private LinearLayoutManager linearLayoutManager;
    private RecyclerView mItemsRecyclerView = null;
    private BillingViewModel billingViewModel;
    private static final String TAG = "BillingActivity";
    private int position = 0;
    CBCustomer cbCustomer;
    private EditText inputProductType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        this.billingViewModel= new BillingViewModel();

        mItemsRecyclerView = findViewById(R.id.rv_product_list);
        String productDetails = getIntent().getStringExtra(PRODUCTS_LIST_KEY);

        if(productDetails != null) {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<CBProduct>>() {}.getType();
            productList = gson.fromJson(productDetails, listType);
        }

        productListAdapter = new ProductListAdapter(this,productList, this);
        linearLayoutManager = new LinearLayoutManager(this);
        mItemsRecyclerView.setLayoutManager(linearLayoutManager);
        mItemsRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mItemsRecyclerView.setAdapter(productListAdapter);

        this.billingViewModel.getProductPurchaseResult().observe(this, status -> {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideProgressDialog();
                    updateSubscribeStatus();
                    if (status) {
                        alertSuccess("Success");
                    } else {
                        alertSuccess("Failure");
                    }
                }
            });
        });

        this.billingViewModel.getSubscriptionStatus().observe(this, status -> {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideProgressDialog();
                    Log.i(TAG, "subscription status :" + status);

                    alertSuccess(status);
                }
            });

        });

        this.billingViewModel.getCbException().observe(this, error -> {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideProgressDialog();
                    Log.i(TAG, "Error from server :" + error);
                    alertSuccess(getCBError(error));
                }
            });
        });
        this.billingViewModel.getError().observe(this, error -> {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideProgressDialog();
                    Log.i(TAG, "error from server:" + error);
                    alertSuccess(error);
                }
            });

        });

    }

    @Override
    public void onProductClick(View view, int position) {
        try {
            this.position = position;
            getCustomerID();
        }catch (Exception exp) {
            Log.e(TAG, "Exception:"+exp.getMessage());
        }
    }

    private void getCustomerID() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_customer_layout);

        EditText input = dialog.findViewById(R.id.productIdInput);
        EditText inputFirstName = dialog.findViewById(R.id.firstNameText);
        EditText inputLastName = dialog.findViewById(R.id.lastNameText);
        EditText inputEmail = dialog.findViewById(R.id.emailText);
        inputProductType = dialog.findViewById(R.id.productTypeText);
        if (isOneTimeProduct()) inputProductType.setVisibility(View.VISIBLE);
        else inputProductType.setVisibility(View.GONE);

        Button dialogButton = dialog.findViewById(R.id.btn_ok);
        dialogButton.setText("Ok");
        dialogButton.setOnClickListener(view -> {
            String customerId = input.getText().toString();
            String firstName = inputFirstName.getText().toString();
            String lastName = inputLastName.getText().toString();
            String email = inputEmail.getText().toString();
            String productType = inputProductType.getText().toString();
            cbCustomer = new CBCustomer(customerId,firstName,lastName,email);
            if (isOneTimeProduct()){
                if (checkProductTypeFiled()) {
                    if (productType.trim().equalsIgnoreCase(OneTimeProductType.CONSUMABLE.getValue())) {
                        purchaseNonSubscriptionProduct(OneTimeProductType.CONSUMABLE);
                    } else if (productType.trim().equalsIgnoreCase(OneTimeProductType.NON_CONSUMABLE.getValue())) {
                        purchaseNonSubscriptionProduct(OneTimeProductType.NON_CONSUMABLE);
                    }
                    dialog.dismiss();
                }
            } else {
                purchaseProduct(customerId);
                // purchaseProduct();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private boolean checkProductTypeFiled(){
        if (inputProductType.getText().toString().length() == 0) {
            inputProductType.setError("This field is required");
            return false;
        }
        return true;
    }

    private boolean isOneTimeProduct(){
        return productList.get(position).getProductType() == ProductType.INAPP;
    }

    private void purchaseProduct(String customerId) {
        showProgressDialog();
        this.billingViewModel.purchaseProduct(this, productList.get(position), customerId);
    }

    private void purchaseProduct() {
        showProgressDialog();
        this.billingViewModel.purchaseProduct(this, productList.get(position), cbCustomer);
    }

    private void purchaseNonSubscriptionProduct(OneTimeProductType productType) {
        showProgressDialog();
        this.billingViewModel.purchaseNonSubscriptionProduct(this, productList.get(position), cbCustomer, productType);
    }

    private void updateSubscribeStatus(){
        productList.get(position).setSubStatus(true);
        productListAdapter.notifyDataSetChanged();
    }


    @Override
    public void onShowProgressBar() {
        showProgressDialog();
    }

    @Override
    public void onHideProgressBar() {
        hideProgressDialog();
    }
}
