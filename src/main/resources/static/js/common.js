function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const mainContent = document.querySelector('.main-content');
    
    // Enable transitions when user clicks (not on page load)
    if (!sidebar.classList.contains('transitions-enabled')) {
        sidebar.classList.add('transitions-enabled');
        if (mainContent) {
            mainContent.classList.add('transitions-enabled');
        }
    }
    
    if (window.innerWidth <= 768) {
        // On mobile, toggle show/hide
        const isShowing = sidebar.classList.toggle('show');
        localStorage.setItem('sidebarMobileShow', isShowing ? 'true' : 'false');
    } else {
        // On desktop, toggle collapsed/expanded
        const isCollapsed = sidebar.classList.toggle('collapsed');
        localStorage.setItem('sidebarCollapsed', isCollapsed ? 'true' : 'false');
    }
}

// Restore sidebar state on page load (without transitions)
(function() {
    document.addEventListener('DOMContentLoaded', function() {
        const sidebar = document.getElementById('sidebar');
        const mainContent = document.querySelector('.main-content');
        if (!sidebar) return;
        
        // Don't enable transitions on page load - only when user clicks
        // This prevents the transition animation when restoring state
        
        if (window.innerWidth <= 768) {
            // Mobile: restore show/hide state
            const sidebarMobileShow = localStorage.getItem('sidebarMobileShow');
            if (sidebarMobileShow === 'false') {
                // Don't show by default on mobile if it was hidden
                sidebar.classList.remove('show');
            }
        } else {
            // Desktop: restore collapsed/expanded state
            const sidebarCollapsed = localStorage.getItem('sidebarCollapsed');
            if (sidebarCollapsed === 'true') {
                sidebar.classList.add('collapsed');
            } else {
                sidebar.classList.remove('collapsed');
            }
        }
        
        // Handle window resize to maintain appropriate state
        let resizeTimeout;
        window.addEventListener('resize', function() {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(function() {
                // Temporarily disable transitions during resize
                const hadTransitions = sidebar.classList.contains('transitions-enabled');
                if (hadTransitions) {
                    sidebar.classList.remove('transitions-enabled');
                    if (mainContent) {
                        mainContent.classList.remove('transitions-enabled');
                    }
                }
                
                if (window.innerWidth <= 768) {
                    // Switched to mobile: remove collapsed class, use show/hide
                    sidebar.classList.remove('collapsed');
                    const sidebarMobileShow = localStorage.getItem('sidebarMobileShow');
                    if (sidebarMobileShow === 'false') {
                        sidebar.classList.remove('show');
                    }
                } else {
                    // Switched to desktop: remove show class, use collapsed/expanded
                    sidebar.classList.remove('show');
                    const sidebarCollapsed = localStorage.getItem('sidebarCollapsed');
                    if (sidebarCollapsed === 'true') {
                        sidebar.classList.add('collapsed');
                    } else {
                        sidebar.classList.remove('collapsed');
                    }
                }
                
                // Re-enable transitions after a short delay if they were enabled
                if (hadTransitions) {
                    setTimeout(function() {
                        sidebar.classList.add('transitions-enabled');
                        if (mainContent) {
                            mainContent.classList.add('transitions-enabled');
                        }
                    }, 50);
                }
            }, 100);
        });
    });
})();

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

// ============================================
// Date Picker Module (Flatpickr)
// ============================================
// Automatically initializes Flatpickr on inputs with class "date-picker" or "datetime-picker"
// Usage:
//   <input type="text" class="date-picker" data-required="true" data-default-today="true">
//   <input type="hidden" name="dateField" class="date-picker-hidden">
//
// Attributes:
//   - class="date-picker" - for date only (format: 18-Apr-2025)
//   - class="datetime-picker" - for date and time (format: 18-Apr-2025 10:25 AM)
//   - data-required="true" - if field is required (defaults to today if empty)
//   - data-default-today="true" - set today as default if empty
//   - data-hidden-input="id" - ID of hidden input for form submission (auto-detected if not specified)
(function() {
    'use strict';
    
    // Format date for display: "18-Apr-2025"
    function formatDateForDisplay(date) {
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const day = date.getDate();
        const month = months[date.getMonth()];
        const year = date.getFullYear();
        return `${day}-${month}-${year}`;
    }
    
    // Format datetime for display: "18-Apr-2025 10:25 AM"
    function formatDateTimeForDisplay(date) {
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const day = date.getDate();
        const month = months[date.getMonth()];
        const year = date.getFullYear();
        let hours = date.getHours();
        const minutes = date.getMinutes();
        const ampm = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12;
        hours = hours ? hours : 12;
        const minutesStr = minutes < 10 ? '0' + minutes : minutes;
        return `${day}-${month}-${year} ${hours}:${minutesStr} ${ampm}`;
    }
    
    // Parse date from display format or hidden input
    function parseInitialDate(inputElement, hiddenElement) {
        // First try to get from hidden input (backend format: yyyy-MM-dd or yyyy-MM-ddTHH:mm)
        if (hiddenElement && hiddenElement.value) {
            const dateStr = hiddenElement.value;
            if (dateStr.includes('T')) {
                return new Date(dateStr);
            } else {
                return new Date(dateStr + 'T00:00:00');
            }
        }
        
        // Try to parse from display input (format: dd-MMM-yyyy or dd-MMM-yyyy HH:mm AM/PM)
        if (inputElement.value) {
            const dateStr = inputElement.value.trim();
            if (!dateStr) return null;
            
            // Try to parse "dd-MMM-yyyy" format
            const parts = dateStr.split(' ');
            const datePart = parts[0];
            const dateParts = datePart.split('-');
            
            if (dateParts.length === 3) {
                const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
                const monthIndex = months.indexOf(dateParts[1]);
                if (monthIndex !== -1) {
                    const day = parseInt(dateParts[0]);
                    const month = monthIndex;
                    const year = parseInt(dateParts[2]);
                    
                    // If there's a time part, parse it
                    if (parts.length >= 3) {
                        const timePart = parts[1];
                        const ampm = parts[2];
                        const [hours, minutes] = timePart.split(':').map(Number);
                        let hour24 = hours;
                        if (ampm === 'PM' && hour24 !== 12) hour24 += 12;
                        if (ampm === 'AM' && hour24 === 12) hour24 = 0;
                        return new Date(year, month, day, hour24, minutes);
                    }
                    
                    return new Date(year, month, day);
                }
            }
        }
        
        return null;
    }
    
    // Initialize Flatpickr for a date/datetime field
    function initializeDatePicker(inputElement) {
        if (!inputElement || inputElement.hasAttribute('data-flatpickr-initialized')) {
            return;
        }
        
        const isDateTime = inputElement.classList.contains('datetime-picker');
        const isRequired = inputElement.getAttribute('data-required') === 'true' || inputElement.hasAttribute('required');
        const defaultToday = inputElement.getAttribute('data-default-today') === 'true' || isRequired;
        
        // Find or create hidden input for form submission
        let hiddenElement = null;
        const hiddenInputId = inputElement.getAttribute('data-hidden-input');
        if (hiddenInputId) {
            hiddenElement = document.getElementById(hiddenInputId);
        } else {
            // Auto-detect: look for hidden input with same name or nearby
            const name = inputElement.getAttribute('name');
            if (name) {
                hiddenElement = document.querySelector(`input[type="hidden"][name="${name}"]`);
            }
            // If not found, look for sibling hidden input
            if (!hiddenElement) {
                hiddenElement = inputElement.parentElement.querySelector('input[type="hidden"]');
            }
        }
        
        // Parse initial date
        let initialDate = parseInitialDate(inputElement, hiddenElement);
        
        // Set default to today if required and no date exists
        if (!initialDate && defaultToday) {
            initialDate = new Date();
        }
        
        // Configure Flatpickr options
        const flatpickrOptions = {
            enableTime: isDateTime,
            dateFormat: isDateTime ? "Y-m-d H:i" : "Y-m-d",
            time_24hr: false,
            defaultDate: initialDate,
            allowInput: false,
            clickOpens: true,
            onChange: function(selectedDates, dateStr, instance) {
                if (selectedDates.length > 0) {
                    const date = selectedDates[0];
                    
                    // Update display with custom format
                    if (isDateTime) {
                        inputElement.value = formatDateTimeForDisplay(date);
                    } else {
                        inputElement.value = formatDateForDisplay(date);
                    }
                    
                    // Update hidden input for form submission in backend format
                    if (hiddenElement) {
                        const year = date.getFullYear();
                        const month = String(date.getMonth() + 1).padStart(2, '0');
                        const day = String(date.getDate()).padStart(2, '0');
                        
                        if (isDateTime) {
                            const hours = String(date.getHours()).padStart(2, '0');
                            const minutes = String(date.getMinutes()).padStart(2, '0');
                            hiddenElement.value = `${year}-${month}-${day}T${hours}:${minutes}`;
                        } else {
                            hiddenElement.value = `${year}-${month}-${day}`;
                        }
                    } else if (inputElement.hasAttribute('name')) {
                        // If no hidden input, update the display input's value directly for submission
                        // But we need to keep the formatted display, so create a hidden input
                        const hidden = document.createElement('input');
                        hidden.type = 'hidden';
                        hidden.name = inputElement.getAttribute('name');
                        const year = date.getFullYear();
                        const month = String(date.getMonth() + 1).padStart(2, '0');
                        const day = String(date.getDate()).padStart(2, '0');
                        if (isDateTime) {
                            const hours = String(date.getHours()).padStart(2, '0');
                            const minutes = String(date.getMinutes()).padStart(2, '0');
                            hidden.value = `${year}-${month}-${day}T${hours}:${minutes}`;
                        } else {
                            hidden.value = `${year}-${month}-${day}`;
                        }
                        inputElement.removeAttribute('name');
                        inputElement.parentElement.insertBefore(hidden, inputElement);
                        hiddenElement = hidden;
                    }
                } else {
                    // Clear values if date is cleared
                    inputElement.value = '';
                    if (hiddenElement) {
                        hiddenElement.value = '';
                    }
                }
            },
            onReady: function(selectedDates, dateStr, instance) {
                // Format initial display value
                if (selectedDates.length > 0) {
                    const date = selectedDates[0];
                    if (isDateTime) {
                        inputElement.value = formatDateTimeForDisplay(date);
                    } else {
                        inputElement.value = formatDateForDisplay(date);
                    }
                    
                    // Initialize hidden input if it exists and is empty
                    if (hiddenElement && !hiddenElement.value) {
                        const year = date.getFullYear();
                        const month = String(date.getMonth() + 1).padStart(2, '0');
                        const day = String(date.getDate()).padStart(2, '0');
                        if (isDateTime) {
                            const hours = String(date.getHours()).padStart(2, '0');
                            const minutes = String(date.getMinutes()).padStart(2, '0');
                            hiddenElement.value = `${year}-${month}-${day}T${hours}:${minutes}`;
                        } else {
                            hiddenElement.value = `${year}-${month}-${day}`;
                        }
                    }
                }
            }
        };
        
        // Initialize Flatpickr
        const flatpickrInstance = flatpickr(inputElement, flatpickrOptions);
        
        // Mark as initialized
        inputElement.setAttribute('data-flatpickr-initialized', 'true');
        
        return flatpickrInstance;
    }
    
    // Initialize all date pickers on page load
    function initializeAllDatePickers() {
        // Check if Flatpickr is loaded
        if (typeof flatpickr === 'undefined') {
            console.warn('Flatpickr is not loaded. Date pickers will not be initialized.');
            return;
        }
        
        // Find all date picker inputs
        const dateInputs = document.querySelectorAll('.date-picker, .datetime-picker');
        dateInputs.forEach(function(input) {
            initializeDatePicker(input);
        });
    }
    
    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeAllDatePickers);
    } else {
        // DOM is already ready
        initializeAllDatePickers();
    }
    
    // Re-initialize after dynamic content is added (for AJAX-loaded content)
    window.reinitializeDatePickers = function() {
        initializeAllDatePickers();
    };
    
    // Export function for manual initialization
    window.initializeDatePicker = initializeDatePicker;
})();


