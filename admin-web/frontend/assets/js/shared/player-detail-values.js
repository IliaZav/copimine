export function resolveDonationBalance(profileDonation = {}, donationLedger = [], donationSessions = []) {
  return profileDonation?.balance
    ?? donationLedger[0]?.balance_after
    ?? donationLedger[0]?.balanceAfter
    ?? donationSessions[0]?.balance_after
    ?? 0;
}
