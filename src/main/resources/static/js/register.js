// UC00: "Please Assign Your Skills :" — add one skill at a time until Submit.
(function () {
    const form = document.getElementById('registerForm');
    const roleSelect = document.getElementById('role');
    const section = document.getElementById('skillsSection');
    const input = document.getElementById('skillInput');
    const addBtn = document.getElementById('addSkillBtn');
    const list = document.getElementById('skillList');

    function currentSkills() {
        return Array.from(list.querySelectorAll('input[name="skills"]')).map(i => i.value.toLowerCase());
    }

    function addSkill() {
        const value = input.value.trim();
        if (!value) {
            input.focus();
            return;
        }
        if (currentSkills().includes(value.toLowerCase())) {
            input.classList.add('is-invalid');
            input.select();
            return;
        }
        const li = document.createElement('li');
        li.className = 'list-inline-item';
        const badge = document.createElement('span');
        badge.className = 'badge text-bg-primary skill-badge';
        const text = document.createElement('span');
        text.textContent = value;
        const hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = 'skills';
        hidden.value = value;
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'btn-close btn-close-white btn-sm ms-1 remove-skill';
        remove.setAttribute('aria-label', 'Remove skill');
        badge.append(text, hidden, remove);
        li.appendChild(badge);
        list.appendChild(li);

        // Ask again for the next skill
        input.value = '';
        input.classList.remove('is-invalid');
        input.focus();
    }

    function toggleSection() {
        const isTech = roleSelect.value === 'TECH';
        section.hidden = !isTech;
        // Only Tech Engineers submit skills
        list.querySelectorAll('input[name="skills"]').forEach(i => { i.disabled = !isTech; });
    }

    addBtn.addEventListener('click', addSkill);
    input.addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addSkill();
        }
    });
    input.addEventListener('input', () => input.classList.remove('is-invalid'));
    list.addEventListener('click', e => {
        if (e.target.classList.contains('remove-skill')) {
            e.target.closest('li').remove();
        }
    });
    roleSelect.addEventListener('change', toggleSection);
    form.addEventListener('submit', () => {
        // A skill typed but not yet added still counts
        if (roleSelect.value === 'TECH' && input.value.trim()) {
            addSkill();
        }
    });

    toggleSection();
})();
