# Crawler Visualisation

## Overview

A realtime visualisation of a live [Google Firebase](https://firebase.google.com/), populated by asynchronous instances of [riot-api-crawler](https://github.com/omarathon/riot-api-crawler) Crawlers.

### Crawler (Data Collection)

Uses riot-api-crawler to obtain a live dataset of Match data for each elo (tier), which uploads its results to a Google Firebase, where each uploaded Match has its tier attached to it.

We run asynchronously 7 crawlers which crawl each tier (BRONZE, SILVER, GOLD, ... CHALLENGER) independently, but send all of their ranked matches to the Firebase. Each crawler "focuses" on its respective tier by having its CrawlerConfig use an EloSummonerFiler and EloMatchFilter configured to their tiers.

### Web Visualisation

Uses Plotly.js to plot a realtime graph streaming from the Google Firebase being populated by the crawler.

On the x axis we have a discrete set of tiers: IRON, BRONZE, SILVER, ..., CHALLENGER.
On the y axis we have the average gold per minute across all of the players for Matches in each tier. 

The gold per minute varies between game time deltas: 0-10 mins, 10-20 mins, ... so we plot different coloured lines for each game time delta.


