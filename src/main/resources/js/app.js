// Filename: app.js

define([
  // These are path alias that we configured in our bootstrap
  'jquery',     				// lib/jquery/jquery
  'underscore', 				// lib/underscore/underscore
  'backbone',    				// lib/backbone/backbone
  '/learningswitch/web/js/views/View.js'	// app
], function($, _, Backbone, View){
  var initialize = function() {
    var view = new View(); // this calls initialize which in turn calls render
  }

  return {
    initialize : initialize
  };
});
