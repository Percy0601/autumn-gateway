import { defineConfig } from 'windicss/helpers'

export default defineConfig({
	scan: {
		dirs: ['src'],
		include: ['**/*.{vue,ts,js,html}'],
		exclude: ['node_modules', 'dist', '.idea']
	}
})
