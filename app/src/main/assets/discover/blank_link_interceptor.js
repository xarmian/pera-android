(function() {
    function handleBlankLinks() {
        // Get all links in the document
        var links = document.getElementsByTagName('a');
        // Loop through each link
        for (var i = 0; i < links.length; i++) {
            var link = links[i];
            // Skip links we've already processed
            if (link.hasAttribute('data-processed')) {
                continue;
            }
            // Mark as processed
            link.setAttribute('data-processed', 'true');
            
            // Check if the link has target="_blank"
            if (link.target === '_blank') {
                // Remove the target attribute
                link.removeAttribute('target');
                // Add a click event listener
                link.addEventListener('click', function(e) {
                    // Prevent default behavior
                    e.preventDefault();
                    // Get the actual href
                    var href = this.href;
                    // Send the href to Android using the correct interface name
                    window.PeraMobileWebInterface.pushNewScreen(JSON.stringify({ url: href }));
                });
            }
        }
    }
    
    // Process existing links
    handleBlankLinks();
    
    // Set up a MutationObserver to handle dynamically added links
    var observer = new MutationObserver(function(mutations) {
        handleBlankLinks();
    });
    
    // Start observing the document with the configured parameters
    observer.observe(document.body, { 
        childList: true, 
        subtree: true 
    });
})(); 