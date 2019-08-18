// initialise Firebase database object
var database = firebase.database();

// node in the Firebase to obtain the data from
var node = 'crawler';

// maximum number of 10min gold deltas to obtain (0-10 mins, 10-20 mins, ...)
var maxDeltas = 6;

// maximum number of points in the graph
var maxPoints = 4000;
// current number of points in the graph
var pointCount = 0;

// the possible ranks
var ranks = ["IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER"];

/* the data stored at each gold delta for each elo,
   where a delta is every one of {0-10mins, 10-20mins, ...}
   and the elos are every one of {IRON, BRONZE, SILVER, ...}
   we store the current average gold per min for that delta,
   with the number of points used to calculate that average
   (use this value to obtain new averages from olds without the old data) */
class EloDeltaData {
    constructor() {
        this.avg = 0;
        this.pointsForAvg = 0;
    }
}

// map of each trace, where each trace stores a Map with key of elo id and value an EloDeltaData
var data = new Map();

// layout of the graph
var layout = {
    title: {
        text:'Visualisation of Riot API Crawler Data',
        font: {
          family: 'Courier New, monospace',
          size: 24
        }
    },
    xaxis: {
        title: {
            text: 'MEAN MATCH ELO',
            font: {
                family: 'Courier New, monospace',
                size: 18,
                color: '#7f7f7f'
            }
        },
        tickmode: "array",
        tickvals: [0, 1, 2, 3, 4, 5, 6, 7, 8], // each elo is indexed from 0 to 8, x component of points have discrete integers between 0 and 8 
        ticktext: ranks,
    },
    yaxis: {
        title: {
            text: 'MEAN GPM PER PLAYER',
            font: {
                family: 'Courier New, monospace',
                size: 18,
                color: '#7f7f7f'
            }
        },
        rangemode: 'tozero'
    }
};

// updates data from the given node of data
function update(val) {
    var totalGold; // total gold for each delta, array
    var participantCount = 0;

    var firstParticipant = true;
    val.match.coreData.participants.forEach(function(participant) {
        // attempt to obtain gold per min deltas, if fail go to next
        var goldDeltas = null;
        try {
            goldDeltas = Object.values(participant.timeline.gold);
        }
        catch (error) {
            return;
        }
        var numDeltas = goldDeltas.length;
        // ensure count deltas initialised (each delta has a default value of 0 gold)
        if (firstParticipant) totalGold = Array(numDeltas - 1).fill(0);
     
        // add to deltas
        totalGold[0] += goldDeltas[0] / 10; // divide by 10 to obtain gold per min
        for (var i = 1; i < Math.min(numDeltas - 1, maxDeltas); i++) {
            // obtain difference in gold across the delta by subtracing the above from the current. divide by 10 for gold per min.
            totalGold[i] += (goldDeltas[i] - goldDeltas[i - 1]) / 10;
        }
        participantCount++;
        firstParticipant = false;
    });

    // preventing divide by 0 error
    if (participantCount === 0) return;

    // divide each delta total by participantCount to obtain averages
    for (var i = 0; i < totalGold.length; i++) {
        totalGold[i] = totalGold[i] / participantCount;
    };

    // obtain rank corresponding to the match from the attached rank object (crawler added this)
    var avgElo = ranks.indexOf(val.rank.tier);

    // now update each delta with new avgs.
    // we compute new averages via https://math.stackexchange.com/questions/106313/regular-average-calculated-accumulatively 
    for (var delta = 0; delta < totalGold.length; delta++) {
        var newAvgPoint = totalGold[delta];
        // if there is no trace for this delta in data then add a new one
        if (!data.has(delta)) {
            data.set(delta, new Map());
        }
        // obtain the relevant trace
        var deltaData = data.get(delta);
        // if there is no EloDeltaData for this elo at this delta in data, then we initialise it
        if (!deltaData.has(avgElo)) {
            deltaData.set(avgElo, new EloDeltaData());
        }
        // we access the specific elo data, i.e data at index of avgElo
        var deltaEloData = deltaData.get(avgElo);
        // update sample size for this avg
        deltaEloData.pointsForAvg++;
        // obtain old avg
        let oldAvg = deltaEloData.avg;
        let newPointsForAvg = deltaEloData.pointsForAvg;
        // use formula to replace with new avg
        deltaEloData.avg = oldAvg + (newAvgPoint - oldAvg) / newPointsForAvg;
    }
    // we have reached the end of update without returning so the point was successfully added
    // so we increase pointCount
    pointCount++;
}

// plots the data on the graph (updates it)
function plot() {
    var chartData = []; // the data for the Plotly graph

    // we go through each delta data, limited by maxDeltas
    for (let delta = 0; delta < maxDeltas; delta++) {
        if (data.has(delta)) { // if the delta exists in data then we create a trace with its data
            var deltaData = data.get(delta);
            var trace = {
                x: [],
                y: [],
                mode: 'lines+markers',
                type: 'scatter',
                name: (10 * delta) + "-" + (10 * (delta + 1)) + " mins",
                line: {shape: 'spline'},
            };
            chartData.push(trace);
    
            // now we add the averages for each elo in the delta to the trace, if it has an average for that elo
            for (let eloId = 0; eloId <= 8; eloId++) {
                if (deltaData.has(eloId)) {
                    trace.x.push(eloId);
                    trace.y.push(deltaData.get(eloId).avg);
                };
            };
        };
    };

    // plot the Plotly graph
    Plotly.newPlot('graph', chartData, layout);

    // set the loaded matches label to the current points
    document.getElementById('loadedMatches').innerHTML = pointCount;
}

// Event for new data added calls append on the new data
database.ref(node).on("child_added", function(snapshot) {
    // only process point if we haven't exceeded maxPoints
    if (pointCount <= maxPoints) {
        var val = snapshot.val();
        // attempt to update the local data with this point
        try {
            update(val);
        }
        catch (err) { // error means we skip the point
            console.log("skipping point!");
            console.log(err);
            return;
        }
        // after adding it if autoupdate is checked then we will automatically plot afterwards
        if (document.getElementById("autoupdateCheckbox").checked) {
            plot();
        }
    };
});

// when plot button is clicked we call plot() to plot the graph
document.getElementById('plotButton').onclick = function() {
    plot(); 
};

// reset the database node
document.getElementById('resetDbButton').onclick = function() {
    database.ref(node).remove();
    // reset local data
    data = new Map();
    // plot new empty graph
    plot();
};

// sleep for 1.5 seconds, then plot and check the auto updating checkbox
new Promise((resolve) => setTimeout(resolve, 1500)).then(() => {
    // plot the empty graph to start
    plot();
    document.getElementById("autoupdateCheckbox").checked = true;
});


