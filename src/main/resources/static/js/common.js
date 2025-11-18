function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('show');
}

// Product Search functionality
(function() {
    let searchTimeout;
    let searchInput, searchClearBtn, searchSuggestions;
    
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
                searchSuggestions.style.display = 'none';
            }
        });
        
        // Handle Enter key
        searchInput.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                const form = document.getElementById('productSearchForm');
                if (form) form.submit();
            } else if (e.key === 'Escape') {
                searchSuggestions.style.display = 'none';
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                const firstItem = searchSuggestions.querySelector('.suggestion-item');
                if (firstItem) {
                    firstItem.focus();
                    firstItem.classList.add('highlighted');
                }
            }
        });
    }
    
    window.handleSearchInput = function(input) {
        clearTimeout(searchTimeout);
        const query = input.value.trim();
        const queryWithoutSpaces = query.replace(/\s/g, '');
        
        if (queryWithoutSpaces.length >= 2) {
            searchTimeout = setTimeout(() => {
                loadSearchSuggestions(query);
            }, 300);
        } else {
            searchSuggestions.style.display = 'none';
        }
    };
    
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
                }
            })
            .catch(error => {
                console.error('Search error:', error);
                searchSuggestions.style.display = 'none';
            });
    }
    
    function displaySearchResults(results) {
        let html = '';
        results.forEach(product => {
            const category = product.category || 'N/A';
            const manufacturer = product.manufacturer || 'N/A';
            const generic = product.generic || 'N/A';
            const dosageForm = product.dosageForm || '';
            const strength = product.strength || '';
            
            html += `
                <div class="suggestion-item" onclick="navigateToProduct(${product.id})">
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
    }
    
    window.navigateToProduct = function(productId) {
        window.location.href = `/products/edit/${productId}`;
    };
    
    window.clearSearch = function() {
        searchInput.value = '';
        searchClearBtn.style.display = 'none';
        searchSuggestions.style.display = 'none';
    };
    
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    // Close suggestions when clicking outside
    document.addEventListener('click', function(event) {
        if (!event.target.closest('.navbar-search-container')) {
            searchSuggestions.style.display = 'none';
        }
    });
    
    // Keyboard navigation in suggestions
    document.addEventListener('keydown', function(e) {
        if (!searchSuggestions || searchSuggestions.style.display === 'none') return;
        
        const items = Array.from(searchSuggestions.querySelectorAll('.suggestion-item'));
        const highlighted = searchSuggestions.querySelector('.suggestion-item.highlighted');
        let currentIndex = highlighted ? items.indexOf(highlighted) : -1;
        
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (currentIndex < items.length - 1) {
                if (highlighted) highlighted.classList.remove('highlighted');
                items[currentIndex + 1].classList.add('highlighted');
                items[currentIndex + 1].scrollIntoView({ block: 'nearest' });
            }
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (currentIndex > 0) {
                if (highlighted) highlighted.classList.remove('highlighted');
                items[currentIndex - 1].classList.add('highlighted');
                items[currentIndex - 1].scrollIntoView({ block: 'nearest' });
            }
        } else if (e.key === 'Enter' && highlighted) {
            e.preventDefault();
            const productId = highlighted.getAttribute('onclick').match(/\d+/)[0];
            navigateToProduct(productId);
        }
    });
})();

