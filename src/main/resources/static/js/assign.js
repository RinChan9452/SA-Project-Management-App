// UC04: filter as soon as a skill is chosen, keep ticked engineers when the filter changes, show the count.
(function () {
    const filterForm = document.getElementById('filterForm');
    const assignForm = document.getElementById('assignForm');
    const skillSelect = document.getElementById('skillFilter');
    const count = document.getElementById('selectedCount');

    function ticked() {
        return Array.from(assignForm.querySelectorAll('input[name="engineerIds"]:checked'));
    }

    filterForm.addEventListener('submit', () => {
        filterForm.querySelectorAll('input[name="selected"]').forEach(i => i.remove());
        ticked().forEach(box => {
            const hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'selected';
            hidden.value = box.value;
            filterForm.appendChild(hidden);
        });
    });
    skillSelect.addEventListener('change', () => filterForm.requestSubmit());

    function updateCount() {
        count.textContent = ticked().length;
    }
    assignForm.addEventListener('change', updateCount);
    updateCount();
})();
