// Filename: main.js

require.config({
  paths: {
    "jquery": "/js/ext/jquery/dist/jquery.min",
    "underscore": "/js/ext/underscore/underscore",
    "backbone": "/js/ext/backbone/backbone",
    "models": "/learningswitch/web/js/models", 			    // app
    "views": "/learningswitch/web/js/views", 				    // app
    "collections": "/learningswitch/web/js/collections" 	// app
  }
});
require([
  'app', '/js/phoenix.js'
], function(App, Phoenix) {
  new App.initialize();
  new Phoenix.initialize();
});
