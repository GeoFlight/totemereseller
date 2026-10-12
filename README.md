# Totem Reseller

A client-side Fabric mod for Minecraft 1.21.11 that reads DonutSMP auction listings, calculates a totem resale price, and can use the in-game Auction House to buy and relist qualifying totems.

## Pricing and trading rules

- The market average is calculated from the per-totem price of each totem listing in the first 10 API pages.
- API `price` is treated as the total listing price and divided by item count. Confirm this matches the current live API response, especially for stacked listings.
- The average is rounded to the nearest 1,000 coins, then 1,000 coins are subtracted for the resale price.
- The mod selects the cheapest listing that leaves at least 5,000 coins of projected profit per totem at that resale price.
- The profit threshold is a gross estimate; it does not account for any server-side listing fees.
- It opens `/ah totem`, clicks a matching visible totem listing, then lists the purchased item with `/ah sell <total-price>` after moving it into the selected hand.
- Scans run about once per minute. The API has no purchase or listing endpoint; those actions are performed through the Minecraft client.

## Setup

1. Install Minecraft 1.21.11, Fabric Loader, Fabric API, and Java 21.
2. Build with `.\gradlew.bat build`. The mod JAR is written to `build\libs`.
3. Put the JAR in the `mods` folder of the Fabric instance you use for DonutSMP.
4. In Minecraft, press **O** to open the mod screen, enter your DonutSMP API key, and press **Save API key**.
5. Join DonutSMP and press **Start**. Press **Stop** at any time to halt further automated actions.

The API key is stored as plain text in the active Fabric instance's `config\totemreseller\api-key.txt` file. Do not share that file. Trading is restricted to servers whose address ends in `donutsmp.net`.

## Limitations

- The API scan is limited to 10 pages per scan.
- The client can only buy listings currently visible in the `/ah totem` screen and only when the displayed tooltip price and stack size match the API listing.
- The automation expects a single click to complete a purchase and `/ah sell <price>` to list the item currently in hand.
- The build and unit tests pass, but the mod has not been exercised against a live DonutSMP session. Monitor the first transaction and stop if the server's GUI or command behavior differs.

## GitHub

The repository intentionally excludes generated build output, local IDE files, `.env` files, and `api-key.txt` credentials. Never commit or publish your API key.

GitHub Actions runs the tests and build on pushes and pull requests. To build locally on Windows, use `.\gradlew.bat test build`.

No project license is included yet. Choose and add a license before describing the project as open source or granting others permission to reuse it.
