<?php
/**
 * Reference: Simpla 2 PayAnyWay Pay URL (SELF callback).
 * Setka Synapse equivalent: handle_payanyway_pay_url → /setka/payanyway/pay
 */
$payload = array_merge($_GET, $_POST);
if (!$payload) {
    http_response_code(200);
    echo 'SUCCESS'; // Moneta health check
    exit;
}

$integrity = $settings['integrity_code']; // from module settings

$amount = isset($payload['MNT_AMOUNT']) ? number_format((float) $payload['MNT_AMOUNT'], 2, '.', '') : '0.00';
$subscriber = $payload['MNT_SUBSCRIBER_ID'] ?? '';
$test_mode = $payload['MNT_TEST_MODE'] ?? '0';

$expected = md5(
    ($payload['MNT_ID'] ?? '') .
    ($payload['MNT_TRANSACTION_ID'] ?? '') .
    ($payload['MNT_OPERATION_ID'] ?? '') .
    $amount .
    ($payload['MNT_CURRENCY_CODE'] ?? '') .
    $subscriber .
    $test_mode .
    $integrity
);

if (strtolower($expected) !== strtolower($payload['MNT_SIGNATURE'] ?? '')) {
    http_response_code(200);
    echo 'FAIL';
    exit;
}

// Mark order paid in shop DB…

http_response_code(200);
echo 'SUCCESS';
