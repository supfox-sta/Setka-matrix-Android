<?php
/**
 * Reference: Simpla 2 PayAnyWay checkout (SELF / Assistant).
 * Setka Synapse equivalent: create_payanyway_payment_request + checkout bridge POST.
 */
class Payanyway
{
    public function checkout_form($order, $settings)
    {
        $mnt_id = $settings['mnt_id'];
        $integrity = $settings['integrity_code'];
        $test_mode = !empty($settings['test_mode']) ? '1' : '0';
        $transaction_id = (string) $order->id;
        $amount = number_format($order->total_price, 2, '.', '');
        $subscriber_id = ''; // Simpla often leaves empty

        $signature = md5($mnt_id . $transaction_id . $amount . 'RUB' . $subscriber_id . $test_mode . $integrity);

        $action = rtrim($settings['payment_url'], '/') . '/assistant.htm';

        return '
<form method="post" action="' . htmlspecialchars($action) . '">
  <input type="hidden" name="MNT_ID" value="' . htmlspecialchars($mnt_id) . '">
  <input type="hidden" name="MNT_TRANSACTION_ID" value="' . htmlspecialchars($transaction_id) . '">
  <input type="hidden" name="MNT_AMOUNT" value="' . htmlspecialchars($amount) . '">
  <input type="hidden" name="MNT_CURRENCY_CODE" value="RUB">
  <input type="hidden" name="MNT_TEST_MODE" value="' . htmlspecialchars($test_mode) . '">
  <input type="hidden" name="MNT_DESCRIPTION" value="Order #' . htmlspecialchars($transaction_id) . '">
  <input type="hidden" name="MNT_SIGNATURE" value="' . htmlspecialchars($signature) . '">
  <input type="submit" value="Pay">
</form>';
    }
}
