import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.common.Division;
import com.merakianalytics.orianna.types.common.Platform;
import com.merakianalytics.orianna.types.common.Queue;
import com.merakianalytics.orianna.types.common.Tier;
import com.merakianalytics.orianna.types.core.summoner.Summoner;
import com.omarathon.riotapicrawler.presets.matchfilters.EloMatchFilter;
import com.omarathon.riotapicrawler.presets.matchfilters.QueueMatchFilter;
import com.omarathon.riotapicrawler.presets.matchformatters.EloMatchFormatter;
import com.omarathon.riotapicrawler.presets.matchformatters.StringMatchFormatter;
import com.omarathon.riotapicrawler.presets.outputhandlers.PostFirebaseOutputHandler;
import com.omarathon.riotapicrawler.presets.outputhandlers.lib.FirebaseConnection;
import com.omarathon.riotapicrawler.presets.outputhandlers.lib.FirebaseDataGenerator;
import com.omarathon.riotapicrawler.presets.outputhandlers.lib.FirebaseDataMatchFormatter;
import com.omarathon.riotapicrawler.presets.summonerfilters.EloSummonerFilter;
import com.omarathon.riotapicrawler.presets.util.Rank;
import com.omarathon.riotapicrawler.presets.util.estimators.CommonMaxMatchEloEstimator;
import com.omarathon.riotapicrawler.presets.util.estimators.MaxSummonerEloEstimator;
import com.omarathon.riotapicrawler.presets.util.estimators.lib.MatchEloEstimator;
import com.omarathon.riotapicrawler.presets.util.estimators.lib.SummonerEloEstimator;
import com.omarathon.riotapicrawler.src.Crawler;
import com.omarathon.riotapicrawler.src.lib.CrawlerConfig;
import com.omarathon.riotapicrawler.src.lib.CrawlerListener;
import com.omarathon.riotapicrawler.src.lib.filter.MatchFilter;
import com.omarathon.riotapicrawler.src.lib.filter.SummonerFilter;
import com.omarathon.riotapicrawler.src.lib.handler.FilteringOutputHandler;
import com.omarathon.riotapicrawler.src.lib.handler.FilteringOutputHandlerFactory;
import net.thegreshams.firebase4j.error.FirebaseException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Main {
    // THROWS an IOException or a FirebaseException if setting up the connection to the Google Firebase failed.
    public static void main(String[] args) throws IOException, FirebaseException {
        // initialise Orianna for this experiment
        setupOrianna();

        // the queues we shall scan for elos of the Summoners
        Set<Queue> leagueQueues = getQueues();

        // the Summoners to run each crawler from
        Map<Tier, Summoner> tierSummonerMap = getTierSummonerMap();

        // List of CrawlerConfigs
        Map<Crawler, Summoner> crawlerSummonerMap = new HashMap<Crawler, Summoner>();

        // construct elo estimators for summoners and matches
        SummonerEloEstimator summonerEloEstimator = new MaxSummonerEloEstimator(leagueQueues);
        MatchEloEstimator matchEloEstimator = new CommonMaxMatchEloEstimator(summonerEloEstimator);

        // Construct a HaltingListener to use as the Listener for the Crawlers
        CrawlerListener listener = new HaltingListener();

        // construct OutputHandler which will format the Match with its elo attached to it, and upload it to a Google Firebase
        FirebaseConnection connection = new FirebaseConnection("https://riot-api-crawler-example-default-rtdb.europe-west1.firebasedatabase.app");
        FirebaseDataGenerator generator = new FirebaseDataGenerator(connection, "crawler");
        StringMatchFormatter stringMatchFormatter = new StringMatchFormatter(new EloMatchFormatter(matchEloEstimator));
        PostFirebaseOutputHandler outputHandler = new PostFirebaseOutputHandler(new FirebaseDataMatchFormatter(stringMatchFormatter, generator));

        // for each tier, create an AverageEloMatchFilter and EloSummonerFilter, and add the Crawler for that Tier
        // to the crawlerSummonerMap, with the Summoner being the base Summoner for that Tier from the tierSummonerMap.
        for (Tier tier : tierSummonerMap.keySet()) {
            // obtain the spanning Ranks across the Tier
            Set<Rank> ranks = getSpanningRanks(tier);

            // construct EloMatchFilter and EloSummonerFilter, which filter for the Ranks specified by the above ranks set,
            // and since the ranks set spans the Tier, they filter elos only for each Tier.
            MatchFilter matchFilter = new EloMatchFilter(ranks, matchEloEstimator);
            SummonerFilter summonerFilter = new EloSummonerFilter(ranks, summonerEloEstimator);

            // construct CrawlerConfig using the above filters, obtaining 10 matches from each Summoner's MatchHistory.
            CrawlerConfig crawlerConfig = new CrawlerConfig(matchFilter, summonerFilter, 10);

            // construct a FilteringOutputHandler which filters matches based upon their Queues,
            // so we discard any match outside of our interested queues.
            FilteringOutputHandler filteringOutputHandler = FilteringOutputHandlerFactory.get(new QueueMatchFilter(leagueQueues), outputHandler);

            // Construct Crawler using our CrawlerConfig and FilteringOutputHandler, and add it the map of Crawlers to Summoners,
            // with the value being the Summoner corresponding to this Tier, obtained from the tierSummonerMap
            crawlerSummonerMap.put(new Crawler(crawlerConfig, filteringOutputHandler, listener), tierSummonerMap.get(tier));
        }

        // now run each crawler with its corresponding base Summoner
        run(crawlerSummonerMap);
    }

    private static void setupOrianna() {
        // we collect the data on EUW
        Orianna.setDefaultPlatform(Platform.EUROPE_WEST);

        // Set API Key for Crawler
        Orianna.setRiotAPIKey("RGAPI-a52d7c3f-e248-4e32-867d-79fcb94a54e6");
    }

    // Returns a set of Ranks that span the lowest division to the highest division for a given Tier
    // (obtains all possible Ranks for a given Tier)
    private static Set<Rank> getSpanningRanks(Tier tier) {
        Division[] divisions = new Division[]{Division.V, Division.IV, Division.III, Division.II, Division.I};
        HashSet<Rank> ranks = new HashSet<Rank>(divisions.length);
        for (Division division : divisions) {
            ranks.add(new Rank(tier, division));
        }
        return ranks;
    }

    // Returns the queues we shall scan for elos of the Summoners
    private static Set<Queue> getQueues() {
        Set<Queue> leagueQueues = new HashSet<Queue>();
        leagueQueues.add(Queue.RANKED_SOLO_5x5);
//        leagueQueues.add(Queue.RANKED_PREMADE_5x5);
//        leagueQueues.add(Queue.RANKED_TEAM_5x5);
        leagueQueues.add(Queue.TEAM_BUILDER_RANKED_SOLO);
        leagueQueues.add(Queue.TEAM_BUILDER_DRAFT_RANKED_5x5);
        return leagueQueues;
    }

    // Returns the base Summoner to crawl from for each elo (Tier) of the Elo Crawler
    private static Map<Tier, Summoner> getTierSummonerMap() {
        HashMap<Tier, Summoner> tierSummonerMap = new HashMap<Tier, Summoner>();
        // now add each Summoner for each Tier
        tierSummonerMap.put(Tier.BRONZE, Summoner.named("Dabby3").get());
        tierSummonerMap.put(Tier.SILVER, Summoner.named("KillerCookie75").get());
        tierSummonerMap.put(Tier.GOLD, Summoner.named("VΛPO").get());
        tierSummonerMap.put(Tier.PLATINUM, Summoner.named("Ghutter Life").get());
        tierSummonerMap.put(Tier.DIAMOND, Summoner.named("smol marie").get());
        tierSummonerMap.put(Tier.MASTER, Summoner.named("MastoPapi").get());
        tierSummonerMap.put(Tier.GRANDMASTER, Summoner.named("Floomer").get());
        tierSummonerMap.put(Tier.CHALLENGER, Summoner.named("Agurin").get());

        return tierSummonerMap;
    }

    // Takes a Map of Crawlers and their corresponding base Summoners, and runs each Crawler
    // with its base Summoner.
    private static void run(Map<Crawler, Summoner> crawlerSummonerMap) {
        for (Crawler crawler : crawlerSummonerMap.keySet()) {
            crawler.run(crawlerSummonerMap.get(crawler));
        }
    }
}
