function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('show');
}

// Product Search functionality
(function() {
    let searchTimeout;
    let searchInput, searchClearBtn, searchSuggestions;
    let highlightedIndex = -1;
    let isSuggestionsVisible = false;
    let isNavigating = false; // Flag to prevent input handler from resetting highlight during navigation
    
    // Initialize search when DOM is ready
    document.addEventListener('DOMContentLoaded', function() {
        searchInput = document.getElementById('productSearchInput');
        searchClearBtn = document.getElementById('searchClearBtn');
        searchSuggestions = document.getElementById('searchSuggestions');
        
        if (searchInput) {
            initializeSearch();
        }
    });
    
    function initializeSearch() {
        // Show/hide clear button based on input
        searchInput.addEventListener('input', function() {
            if (this.value.length > 0) {
                searchClearBtn.style.display = 'block';
            } else {
                searchClearBtn.style.display = 'none';
                hideSuggestions();
            }
            
            // Reset navigation flag when user types (typing means they're not navigating)
            isNavigating = false;
            handleSearchInput(this.value);
        });
        
        // Handle keyboard navigation
        searchInput.addEventListener('keydown', function(e) {
            // Only handle navigation keys if suggestions are visible
            if (!isSuggestionsVisible) {
                // Allow Enter to submit form if suggestions are not visible
                if (e.key === 'Enter') {
                    return; // Let form submit normally
                }
                return;
            }
            
            const suggestionItems = searchSuggestions.querySelectorAll('.suggestion-item:not(.no-results)');
            
            switch(e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    e.stopPropagation();
                    // Cancel any pending search to prevent results from resetting highlight
                    clearTimeout(searchTimeout);
                    isNavigating = true; // Set flag to prevent input handler from resetting
                    if (suggestionItems.length > 0) {
                        highlightedIndex = highlightedIndex < suggestionItems.length - 1 ? highlightedIndex + 1 : 0;
                        updateHighlight(suggestionItems);
                        scrollToHighlighted(suggestionItems[highlightedIndex]);
                    }
                    break;
                    
                case 'ArrowUp':
                    e.preventDefault();
                    e.stopPropagation();
                    // Cancel any pending search to prevent results from resetting highlight
                    clearTimeout(searchTimeout);
                    isNavigating = true; // Set flag to prevent input handler from resetting
                    if (suggestionItems.length > 0) {
                        highlightedIndex = highlightedIndex > 0 ? highlightedIndex - 1 : suggestionItems.length - 1;
                        updateHighlight(suggestionItems);
                        scrollToHighlighted(suggestionItems[highlightedIndex]);
                    }
                    break;
                    
                case 'Enter':
                    e.preventDefault();
                    if (highlightedIndex >= 0 && suggestionItems.length > 0 && suggestionItems[highlightedIndex]) {
                        // Navigate to highlighted product
                        const productId = suggestionItems[highlightedIndex].getAttribute('data-product-id');
                        if (productId) {
                            navigateToProduct(parseInt(productId));
                        }
                    } else {
                        // Submit form if no suggestion is highlighted
                        const form = document.getElementById('productSearchForm');
                        if (form) form.submit();
                    }
                    break;
                    
                case 'Escape':
                    e.preventDefault();
                    hideSuggestions();
                    break;
            }
        });
    }
    
    function handleSearchInput(value) {
        clearTimeout(searchTimeout);
        const query = value.trim();
        const queryWithoutSpaces = query.replace(/\s/g, '');
        
        // Reset highlight when user types (not when navigating)
        if (!isNavigating) {
            highlightedIndex = -1;
        }
        
        if (queryWithoutSpaces.length >= 2) {
            searchTimeout = setTimeout(() => {
                loadSearchSuggestions(query);
            }, 300);
        } else {
            hideSuggestions();
        }
    }
    
    function showSuggestions() {
        if (searchSuggestions.style.display !== 'none' && searchSuggestions.querySelectorAll('.suggestion-item:not(.no-results)').length > 0) {
            isSuggestionsVisible = true;
        }
    }
    
    function hideSuggestions() {
        searchSuggestions.style.display = 'none';
        isSuggestionsVisible = false;
        highlightedIndex = -1;
    }
    
    function updateHighlight(suggestionItems) {
        suggestionItems.forEach((item, index) => {
            if (index === highlightedIndex) {
                item.classList.add('highlighted');
            } else {
                item.classList.remove('highlighted');
            }
        });
    }
    
    function scrollToHighlighted(element) {
        if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    }
    
    function loadSearchSuggestions(query) {
        const queryWithoutSpaces = query.replace(/\s/g, '');
        if (queryWithoutSpaces.length < 2) {
            searchSuggestions.style.display = 'none';
            return;
        }
        
        fetch(`/products/api/search?q=${encodeURIComponent(query)}`)
            .then(response => response.json())
            .then(data => {
                if (data && data.length > 0) {
                    displaySearchResults(data);
                } else {
                    searchSuggestions.innerHTML = `
                        <div class="suggestion-item no-results">
                            <i class="bi bi-search me-2"></i>
                            <span>No products found for "${escapeHtml(query)}"</span>
                        </div>
                    `;
                    searchSuggestions.style.display = 'block';
                    isSuggestionsVisible = false; // No suggestions to navigate
                }
            })
            .catch(error => {
                console.error('Search error:', error);
                searchSuggestions.style.display = 'none';
            });
    }
    
    function displaySearchResults(results) {
        let html = '';
        // Only reset highlight if user is not currently navigating
        if (!isNavigating) {
            highlightedIndex = -1;
        }
        
        results.forEach((product, index) => {
            const category = product.category || 'N/A';
            const manufacturer = product.manufacturer || 'N/A';
            const generic = product.generic || 'N/A';
            const dosageForm = product.dosageForm || '';
            const strength = product.strength || '';
            
            html += `
                <div class="suggestion-item" 
                     data-product-id="${product.id}"
                     data-index="${index}"
                     onclick="navigateToProduct(${product.id})">
                    <div class="suggestion-item-header">
                        <i class="bi bi-capsule me-2 text-primary"></i>
                        <strong>${escapeHtml(product.name)}</strong>
                    </div>
                    <div class="suggestion-item-details">
                        <span class="badge bg-secondary me-2">${escapeHtml(category)}</span>
                        ${dosageForm ? `<span class="badge bg-info me-2"><i class="bi bi-capsule-pill me-1"></i>${escapeHtml(dosageForm)}</span>` : ''}
                        ${manufacturer !== 'N/A' ? `<span class="text-muted"><i class="bi bi-building me-1"></i>${escapeHtml(manufacturer)}</span>` : ''}
                        ${generic !== 'N/A' ? `<span class="text-muted ms-2"><i class="bi bi-pill me-1"></i>${escapeHtml(generic)}</span>` : ''}
                        ${strength ? `<span class="text-muted ms-2"><i class="bi bi-info-circle me-1"></i>${escapeHtml(strength)}</span>` : ''}
                    </div>
                </div>
            `;
        });
        
        searchSuggestions.innerHTML = html;
        searchSuggestions.style.display = 'block';
        isSuggestionsVisible = true;
        
        // Add mouse hover support
        const suggestionItems = searchSuggestions.querySelectorAll('.suggestion-item:not(.no-results)');
        suggestionItems.forEach((item, index) => {
            item.addEventListener('mouseenter', function() {
                highlightedIndex = index;
                updateHighlight(suggestionItems);
            });
        });
    }
    
    window.navigateToProduct = function(productId) {
        window.location.href = `/products/edit/${productId}`;
    };
    
    window.clearSearch = function() {
        searchInput.value = '';
        searchClearBtn.style.display = 'none';
        hideSuggestions();
    };
    
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    // Close suggestions when clicking outside
    document.addEventListener('click', function(event) {
        if (!event.target.closest('.navbar-search-container')) {
            hideSuggestions();
        }
    });
})();


