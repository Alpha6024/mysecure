package com.mysecure.mysecure.scanner;

public interface SecurityScanner {
    ScanResult scan(ScanRequest request);
    ScannerType getScannerType();
}
