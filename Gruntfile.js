module.exports = function(grunt) {

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        "download-electron": {
            version: "1.4.5",
            outputDir: "./electron",
            // if you want to download electron into project directory
            // downloadDir: ".electron-download",
            rebuild: true
        }
    });

    grunt.loadNpmTasks('grunt-download-electron');

};
