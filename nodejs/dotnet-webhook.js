/**
*  This is a sample webhook server that listens for webhook
*  callbacks coming from Trello, and updates any cards that are
*  added or modified so everyone knows they are "PRIORITY"
*
*  To get started
*  * Add your key and token below
*  * Install dependencies via `npm install express request body-parser`
*  * Run `node app.js` on a publicly visible IP
*  * Register your webhook and point to http://<ip or domain>:3123/priority
*
*  Note that this sample does not authenticate incoming signatures,
*  which Trello DOES support.
*/


var express  = require('express'),
    request  = require('request'),
    jenkins = '',
    app      = express(),
    bodyParser = require('body-parser'),
    port = process.env.PORT || 3123,
    env = process.env.NODE_ENV || 'development',
    key = "YOUR KEY",
    token = "YOUR TOKEN";

process.argv.forEach(function (val, index, array) {
    if (index == 2) {
        token = val;
        jenkins = require('jenkins')({ baseUrl: 'http://dotnet:' + token + '@52.79.132.74:8080', crumbIssuer: true });
    }
});

// Allows us to easily read the payload from the webhook
app.use( bodyParser.json({ type: 'application/vnd.myget.webhooks.v1+json' }) );

app.post('/webhook/dotnet-core', function (request, response) {

    var bodyObj = request.body;
    var type = bodyObj.PayloadType;

    // request log
    console.log('======================', new Date() ,'======================');
    console.log('[request]', request.body);

    jenkins.info(function(err, data) {
        if (err) throw err;
        console.log('info', data);
    });

    console.log('payload type: ', type);
    if (type == 'PackageAddedWebHookEventPayloadV1') {
        jenkins.job.build({ name: 'manage_myget/manage_tizen_nupkgs', parameters: { 'PUSH_METADATA':  JSON.stringify(bodyObj)}}, function(err, data) {
            if (err) { return console.log(err); }
            console.log('info', data);
        });
    }

    response.sendStatus(200);
});

// respond with "hello world" when a GET request is made to the homepage
app.get('/webhook/dotnet-core', function(req, res) {
  res.send('This is webhook server for dotnet-core');
});


// Standard NodeJS Listener
var server = app.listen(port, function () {
     var host = server.address().address;
     var port = server.address().port;

     console.log('Priority Enforcer listening at http://%s:%s in %s', host, port, env);
});
