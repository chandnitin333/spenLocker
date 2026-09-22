# SpendLocker Privacy Policy

_Last updated: 2026-09-22_

SpendLocker is a personal finance and document vault application that runs entirely on
your own computer. This policy explains what happens to your data.

## Data storage

All of your financial records, documents, and settings are stored in a single encrypted
database file (an AES-256 encrypted SQLite vault) on your own device. SpendLocker has no
server. The developer of SpendLocker cannot see, access, collect, or transmit any of your
data — there is no backend to send it to.

## Google account access

SpendLocker offers optional integrations with your own Google account:

- **Google Drive** (`drive.file` scope): lets you back up and restore documents you
  choose to upload, and browse/download files from your Drive, directly between your
  device and your Google Drive. SpendLocker only accesses files it created or that you
  explicitly open through its file picker — it cannot browse your entire Drive.
- **Gmail** (`https://mail.google.com/` scope, IMAP): lets you pull email attachments
  (e.g. receipts, invoices) into your document vault. This connects directly from your
  device to Gmail's IMAP servers using your Google account's authorization — no email
  content passes through any SpendLocker server, because none exists.

These integrations use Google's standard OAuth 2.0 consent flow. You grant access
directly to Google; SpendLocker only receives the access token Google issues, which is
stored locally on your device (not transmitted anywhere else) and used solely to make
requests to Google's own APIs on your behalf.

You can revoke this access at any time from SpendLocker's Settings screen ("Disconnect"),
or directly from your Google Account's
[Security settings](https://myaccount.google.com/permissions).

## What SpendLocker does not do

- It does not have a server, so it cannot collect, store, or sell your data anywhere
  outside your own device.
- It does not share your data with any third party.
- It does not use your data for advertising.

## Contact

Questions about this policy: chand.nitin333@gmail.com
