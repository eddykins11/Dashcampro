package io.kasava.data;

import java.util.Date;

public class UsbId {
    public int vendorId;
    public int productId;

    public UsbId (int vendorId, int productId) {
        this.vendorId = vendorId;
        this.productId = productId;
    }
}